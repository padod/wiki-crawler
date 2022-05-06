package sandbox.app
package crawler.WikiCrawlerApp

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable

class StateFulVisitedCheck extends GraphStage[FlowShape[Url, Url]] {

  val in: Inlet[Url] = Inlet[Url]("AccumulateWhileUnchanged.in")
  val out: Outlet[Url] = Outlet[Url]("AccumulateWhileUnchanged.out")

  override def shape: FlowShape[Url, Url] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private val visitedUrls: mutable.Set[String] = mutable.Set()
    val buffer: mutable.Queue[Url] = mutable.Queue[Url]()
    def bufferFull: Boolean = buffer.size == 100
    var downstreamWaiting = false

//    override def preStart(): Unit = {
//      // a detached stage needs to start upstream demand
//      // itself as it is not triggered by downstream demand
//      if (!visitedUrls.contains(url.url)) {pull(in)
//    }

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val url = grab(in)
          if (!visitedUrls.contains(url.url)) {
            println(s"Enqueuing not visited url ${url.url}")
            buffer.enqueue(url)
            visitedUrls += url.url
            if (downstreamWaiting) {
              downstreamWaiting = false
              val bufferedElem = buffer.dequeue()
              push(out, bufferedElem)
            }
          } else {
            println(s"Dequeueing visited url ${url.url}")
            Thread.sleep(100)
          }
          if (!bufferFull) {
            pull(in)
          }
        }

      })

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (buffer.isEmpty) {
            downstreamWaiting = true
          } else {
            val url = buffer.dequeue()
            push(out, url)
          }
          if (!bufferFull && !hasBeenPulled(in)) {
            pull(in)
          }
        }
      })
  }
}