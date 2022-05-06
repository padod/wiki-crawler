package sandbox.app
package crawler.WikiCrawlerApp


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.jsoup.Jsoup

import java.net.URL
import akka.http.scaladsl.model.HttpMethods.GET
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.{Future}
import scala.util.Try


case class Url(url: String, depth: Long)

object Main extends App {

  implicit val system: ActorSystem = ActorSystem("wiki-crawler")

  import system.dispatcher

  val matValuePoweredSource = Source.queue[Url](bufferSize = 100, OverflowStrategy.backpressure)
  val (sourceMat, source) = matValuePoweredSource.preMaterialize()
  val visited = Set()

  sourceMat.offer(Url("https://nivox.github.io/posts/akka-stream-materialized-values/", 3))

  def runRequest(url: Url): Future[String] = {
    val res = Http().singleRequest(HttpRequest(method = GET, uri = url.url))
    // TODO: move check to pipeline
    res.flatMap{
      case HttpResponse(StatusCodes.OK, headers, entity, _) => entity.dataBytes.runReduce(_ ++ _).map(_.utf8String)
      case resp @ HttpResponse(code, _, _, _) => println(s"${url.url} failed, response code: " + code)
                                                  resp.discardEntityBytes()
                                                    Future("")
    }
  }

  def pushBack(url: Url): Unit = {
    sourceMat.offer(url)
    println(s"Pushed to queue ${url.url}")
  }

  val stateFulVisitedCheck = Flow.fromGraph(new StateFulVisitedCheck)

  source.mapAsync(1)(url => runRequest(url).map((url, _)))
    .map(urlResp => parseUrls(urlResp))
    .mapConcat(identity)
    .via(stateFulVisitedCheck)
    .map(url => Url(url.url, url.depth - 1))
    .filter(url => url.url != null && url.url != "")
    .map(url => if (url.depth > 0) { pushBack(url) } else println(s"Dropped $url"))
    .mapAsync(1) { i =>
      Future {
        Thread.sleep(100)
        i
      }
    }
//    .idleTimeout(5.seconds)
//    .recoverWithRetries(1, {case _: TimeoutException => Source.empty})
    .to(Sink.ignore).run()


  def parseUrls: ((Url, Object)) => List[Url] = {
    case (url, resp) => Jsoup.parse(resp.toString).select("a[href]").toList.map(_.attr("href"))
      .map(l => if (l.matches("^[\\/#].*")) url + l else l)
      .filter(l => Try(new URL(l)).isSuccess)
      .map(Url(_, url.depth))
  }
}