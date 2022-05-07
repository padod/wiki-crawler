package sandbox.app
package crawler.WikiCrawlerApp
import crawler.StateFulVisitedCheck

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.{ActorAttributes, OverflowStrategy, StreamTcpException, Supervision}
import akka.stream.scaladsl.{FileIO, Flow, Sink, Source}
import org.jsoup.Jsoup

import scala.util.Try
import java.io.File
import java.net.URL
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.Future


case class Url(url: String, depth: Long)


object CrawlerApp extends App {

  implicit val system: ActorSystem = ActorSystem("wiki-crawler")

  val decider: Supervision.Decider = {
    case _: StreamTcpException => Supervision.Resume
    case e => system.log.error(s"$e"); Supervision.Stop
  }

  import system.dispatcher

  val matValuePoweredSource = Source.queue[Url](bufferSize = 100, OverflowStrategy.backpressure)
  val (sourceMat, source) = matValuePoweredSource.preMaterialize()

  sourceMat.offer(Url("https://doc.akka.io/docs/akka-http/current/introduction.html", 3))

  def md5(s: String): String = {
    import java.math.BigInteger
    import java.security.MessageDigest
    val md = MessageDigest.getInstance("MD5")
    val digest: Array[Byte] = md.digest(s.getBytes)
    val bigInt = new BigInteger(1, digest)
    bigInt.toString(16).trim
  }

  def runRequestAndSave(url: Url): Future[String] = {
    val res = Http().singleRequest(Get(uri = url.url))
    res.flatMap{
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        entity
          .dataBytes
          .alsoTo(FileIO.toPath(new File(md5(url.url)).toPath))
          .runReduce(_ ++ _).map(_.utf8String)
      case resp @ HttpResponse(code, _, _, _) => system.log.error(s"${url.url} failed, response code: " + code)
        resp.discardEntityBytes()
        Future("")
    }
  }

  def pushBack(url: Url): Unit = {
    sourceMat.offer(url)
    system.log.info(s"Pushed to queue $url")
  }

  def parseUrls: ((Url, String)) => List[Url] = {
    case (url, resp) => Jsoup.parse(resp).select("a[href]").toList.map(_.attr("href"))
      .map(l => if (l.matches("^[\\/#].*")) url + l else l)
      .filter(l => Try(new URL(l)).isSuccess)
      .map(Url(_, url.depth))
  }

  val stateFulVisitedCheck = Flow.fromGraph(new StateFulVisitedCheck)

  source
    .mapAsyncUnordered(1)(url => runRequestAndSave(url).map((url, _)))
    .withAttributes(ActorAttributes.supervisionStrategy(decider))
    .map(urlResp => parseUrls(urlResp))
    .mapConcat(identity)
    .via(stateFulVisitedCheck)
    .map(url => Url(url.url, url.depth - 1))
    .filter(url => url.url != null && url.url != "")
    .map(url => if (url.depth > 0)  { pushBack(url) } else system.log.info(s"Reached final depth with ${url.url}"))
    .runWith(Sink.ignore).onComplete(_ => system.terminate())
}