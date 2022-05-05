package sandbox.app
package crawler.WikiCrawlerApp

import scala.util.{Success, Failure}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.MergeHub.source
import akka.stream.{ActorMaterializer, ClosedShape, CompletionStrategy, OverflowStrategy}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}
import org.jsoup.Jsoup
import akka.stream.typed.scaladsl.ActorSource

import java.net.{URI, URL}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.Try

case class Url(url: String, depth: Long)

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("wiki-crawler")

  import system.dispatcher

  val matValuePoweredSource = Source.queue[Url](bufferSize = 100, OverflowStrategy.backpressure)
  val (sourceMat, source) = matValuePoweredSource.preMaterialize()
  val visited = Set()

  sourceMat.offer(Url("https://en.wikipedia.org/wiki/Taxi", 4))

  def runRequest(url: Url): Future[String] = {
    val res = Http().singleRequest(HttpRequest(method = GET, uri = url.url))
    // TODO: move check to pipeline
    res.flatMap{
      case HttpResponse(StatusCodes.OK, headers, entity, _) => entity.dataBytes.runReduce(_ ++ _).map(_.utf8String)
      case resp @ HttpResponse(code, _, _, _) => println("Request failed, response code: " + code)
                                                  resp.discardEntityBytes()
                                                    Future("")
    }
  }

  def pushBack(url: Url): Unit = {
    sourceMat.offer(url)
    println(s"Pushed to queue $url")
  }

  source.mapAsync(1)(url => runRequest(url).map((url, _)))
    .map(urlResp => parseUrls(urlResp))
    .mapConcat(identity)
    .map(url => Url(url.url, url.depth - 1))
    .filter(url => url.url != null && url.url != "")
    .map(url => if (url.depth > 0) { pushBack(url) } else println(s"Dropped $url"))
    .mapAsync(1) { i =>
      Future {
        system.log.info(s"Start task $i")
        Thread.sleep(100)
        system.log.info(s"Start task $i")
        i
      }
    }
    .idleTimeout(5.seconds)
    .recoverWithRetries(1, {case _: TimeoutException => Source.empty}).to(Sink.ignore).run()


  def parseUrls: ((Url, Object)) => List[Url] = {
    case (url, resp) => Jsoup.parse(resp.toString).select("a[href]").toList.map(_.attr("href"))
      .map(l => if (l.matches("^[\\/#].*")) url + l else l)
      .filter(l => Try(new URL(l)).isSuccess)
      .map(Url(_, url.depth))
  }
}
//}


//object Main2 extends App {
//
//  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
//    import GraphDSL.Implicits._
//
//    val zip = b.add(ZipWith((left: Int, right: Int) => left))
//    val bcast = b.add(Broadcast[Int](2))
//    val concat = b.add(Concat[Int]())
//    val start = Source.single(0)
//
//    def getUrl(url: Url): PageBody = {
//      PageBody()
//    }
//
//    def parseUrls(pagebody: PageBody): Option[List[Url]] = {
//      List[Url]
//    }
//
//    source ~> zip.in0
//    zip.out.map { s => println(s); s } ~> bcast ~> Sink.ignore
//    zip.in1 <~ concat <~ start
//    concat         <~          bcast
//    ClosedShape
//
//    source ~> getUrlAndSave ~> parseUrls ~> Sink.ignore
//    getUrl <~ parseUrls
//
//  })
//
//}