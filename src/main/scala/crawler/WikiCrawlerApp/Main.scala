package sandbox.app
package crawler.WikiCrawlerApp

import akka.actor.Status.Success
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
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
import scala.concurrent.{Await, Future}
import scala.util.Try

case class Url(url: String, depth: Long)

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("wiki-crawler")

  import system.dispatcher

  val responseFuture =
    Http().singleRequest(HttpRequest(uri = "https://index.scala-lang.org/nielsenbe/spark-wiki-parser"))
  val timeout = 5.second
  val responseAsString = Await.result(
    responseFuture
      .flatMap { resp => resp.entity.toStrict(timeout) }
      .map { strictEntity => strictEntity.data.utf8String },
    timeout
  )

  //  def responseAsString: Url => (Url, String) = {
  //    url => Http().singleRequest(HttpRequest(uri=url.url)) =>
  //  }

  val matValuePoweredSource = Source.queue[Url](bufferSize = 10, OverflowStrategy.backpressure)
  val (sourceMat, source) = matValuePoweredSource.preMaterialize()
  val visited = Set()

  sourceMat.offer(Url("https://index.scala-lang.org/nielsenbe/spark-wiki-parser", 3))

  def runRequest(url: Url): Future[String] = {
    Http()
      .singleRequest(HttpRequest(method = GET, uri = url.url))
      .flatMap { response =>
        response.entity.dataBytes
          .runReduce(_ ++ _)
          .map(_.utf8String)
      }
  }

  def pushBack(url: Url): Unit = {
    sourceMat.offer(url)
    println(s"Pushed to queue $url")
  }

  source.mapAsync(4)(url => runRequest(url).map((url, _)))
    .map(urlResp => parseUrls(urlResp))
    .mapConcat(identity)
    .filter(url => url.url != null && url.url != "")
    .map(url => if (url.depth > 0) { pushBack(url) } else println(s"Dropped $url"))
    .to(Sink.ignore).run()


  def parseUrls: ((Url, String)) => List[Url] = {
    case (url, resp) => Jsoup.parse(resp).select("a[href]").toList.map(_.attr("href"))
      .map(l => if (l.matches("^[\\/#].*")) url + l else l)
      .filter(l => Try(new URI(l)).isSuccess)
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