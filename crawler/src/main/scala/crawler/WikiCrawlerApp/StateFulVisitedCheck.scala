package crawler.WikiCrawlerApp

import scala.collection.mutable
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler, StageLogging}

class StateFulVisitedCheck extends GraphStage[FlowShape[Url, Url]] {

  val in: Inlet[Url] = Inlet[Url]("StateFulVisitedCheck.in")
  val out: Outlet[Url] = Outlet[Url]("StateFulVisitedCheck.out")

  override def shape: FlowShape[Url, Url] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape)
    with StageLogging {

    private val visitedUrls: mutable.Set[String] = mutable.Set()

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val url = grab(in)
          if (!visitedUrls.contains(url.url)) {
            visitedUrls += url.url
            push(out, url)
          } else {
            log.info(s"Dequeueing visited url ${url.url}")
            pull(in)
          }
        }
      }
    )

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (!hasBeenPulled(in)) {
            pull(in)
          }
        }
      })
  }
}