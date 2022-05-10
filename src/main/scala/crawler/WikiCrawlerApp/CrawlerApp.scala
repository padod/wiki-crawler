package sandbox.app
package crawler.WikiCrawlerApp


import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpResponse, IllegalUriException, StatusCodes}
import akka.stream.{ActorAttributes, ClosedShape, IOResult, OverflowStrategy, StreamTcpException, Supervision}
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString
import org.jsoup.Jsoup
import spray.json._

import java.io.File
import java.nio.file.Paths
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt


case class Url(url: String, depth: Long)


object CrawlerApp extends App {

  import system.dispatcher

  implicit val system: ActorSystem = ActorSystem("wiki-crawler")

  object JsonSerializer extends DefaultJsonProtocol {
    implicit val serializedDoc: RootJsonFormat[Document] = jsonFormat4(Document)
  }

  import JsonSerializer._

  private val decider: Supervision.Decider = {
    case _: StreamTcpException => Supervision.Resume
    case _: IllegalUriException => Supervision.Resume
    case e => system.log.error(s"$e"); Supervision.Stop
  }

  val directory = new File("scraped")
  if (!directory.exists) directory.mkdir


  def md5(s: String): String = {
    import java.math.BigInteger
    import java.security.MessageDigest
    val md = MessageDigest.getInstance("MD5")
    val digest: Array[Byte] = md.digest(s.getBytes)
    val bigInt = new BigInteger(1, digest)
    bigInt.toString(16).trim
  }

  // using verbose actor api here, bc I didn't find a way to dig out response status codes otherwise
  def runRequest: Url => Future[String] = {
    url => Http().singleRequest(Get(uri = url.url))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          entity
            .withoutSizeLimit()
            .dataBytes
            .runReduce(_ ++ _)
            .map(_.utf8String)
        // no error handling, just drain the bytes
        case resp@HttpResponse(code, _, _, _) => system.log.error(s"${url.url} failed, response code: " + code)
          resp.discardEntityBytes()
          Future("")
      }
  }

  case class Document(
                       id: String,
                       nodeUrl: String,
                       BodyText: String,
                       childNodes: List[String]
                     )

  def sink(d: Document): Document = {
    Future.successful(
      FileIO
        .toPath(Paths.get(s"/Users/padod/Projects/queue-crawler/scraped/${d.id}.json"))
        .mapMaterializedValue(_.flatMap(_ => Future.successful(Done)))
    )
    d
  }

  def parsePageBody(urlResp: (Url, String)): Document = {
    val (url, responseBody) = urlResp
    val childUrls = Jsoup.parse(responseBody).select("a[href]").toList
      .map(_.attr("href"))
      .filter(_.matches("\\/wiki\\/[^.]+$"))
      // employing the fact that internal links lead to article in the same language space in wiki
      .map(l => "https://en.wikipedia.org" + l)
    val text = Jsoup.parse(responseBody).select("#content").text()
    Document(md5(url.url), url.url, text, childUrls)
  }

  // prematerialization of the source is needed to kick off the recursive process of incoming url flow
  val matValuePoweredSource = Source.queue[Url](bufferSize = 100, OverflowStrategy.backpressure)
  val (sourceMat, source) = matValuePoweredSource.preMaterialize()

  sourceMat.offer(Url("https://en.wikipedia.org/wiki/Akka_(toolkit)", 5))

  def pushBack(url: Url): Unit = {
    sourceMat.offer(url)
    system.log.info(s"Pushed to queue $url")
  }

  val stateFulVisitedCheck = Flow.fromGraph(new StateFulVisitedCheck)

  def writeJsonFlow(doc: Document): Sink[Document, Future[IOResult]] = {
    Flow[Document]
      .map { doc => ByteString(doc.toJson.compactPrint) }
      .toMat(FileIO.toPath(Paths.get(s"/Users/padod/Projects/queue-crawler/scraped/${doc.id}.json")))(Keep.right)
  }

  val writeFlow: Sink[Document, NotUsed] =
    Flow[Document].mapAsync(parallelism = 4){ doc =>
      system.log.info(s"Writing ${doc.nodeUrl} to ${doc.id}.json")
      Source.single(ByteString(doc.toJson.compactPrint)).runWith(FileIO.toPath(Paths.get(s"/Users/padod/Projects/queue-crawler/scraped/${doc.id}.json")))
    }.to(Sink.foreach[IOResult]{println})

  val continueFlow: Sink[(Url, Document), NotUsed] = Flow[(Url, Document)]
    .map { case (url, doc) => doc.childNodes.map(node => Url(node, url.depth-1))}
    .mapConcat(identity)
    .via(stateFulVisitedCheck)
    .filter(url => url.url != null && url.url != "")
    .map(url => if (url.depth > 0)  { pushBack(url) } else system.log.info(s"Reached final depth with ${url.url}"))
    .to(Sink.ignore)

  val start = source
    // since the source is alive forever, explicit shutdown after it stops emitting new elements
    .idleTimeout(10.seconds)
    .withAttributes(ActorAttributes.supervisionStrategy(decider))
    .mapAsync(1)(url => runRequest(url).map((url, _)))
    .map { case (url, resp) => (url, parsePageBody((url ,resp))) }

  val graph = GraphDSL.createGraph(writeFlow, continueFlow)(Keep.left) { implicit builder =>
    (write, continue) =>
      import GraphDSL.Implicits._

      val bcast = builder.add(Broadcast[(Url, Document)](2))
      val dropUrl = Flow[(Url, Document)].map { case (_, doc) => doc }
      start ~> bcast
      bcast.out(0) ~> dropUrl ~> write
      bcast.out(1) ~> continue
      ClosedShape
  }

  RunnableGraph
    .fromGraph(graph).run()

}