package org.thp.thehive.migration.th3

import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import org.thp.scalligraph.SearchError
import play.api.Logger
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class SearchWithScroll(client: ElasticClient, docType: String, query: JsObject, keepAliveStr: String)(implicit
    ec: ExecutionContext
) extends GraphStage[SourceShape[JsValue]] {

  private[SearchWithScroll] lazy val logger = Logger(getClass)
  val out: Outlet[JsValue]                  = Outlet[JsValue]("searchHits")
  val shape: SourceShape[JsValue]           = SourceShape.of(out)
  val firstResults: Future[JsValue]         = client.search(docType, query, "scroll" -> keepAliveStr)

  def readHits(searchResponse: JsValue): Seq[JsObject] =
    (searchResponse \ "hits" \ "hits").as[Seq[JsObject]].map { hit =>
      (hit \ "_source").as[JsObject] +
        ("_id" -> (hit \ "_id").as[JsValue]) +
        ("_parent" -> (hit \ "_parent")
          .asOpt[JsValue]
          .orElse((hit \ "_source" \ "relations" \ "parent").asOpt[JsValue])
          .getOrElse(JsNull))
    }
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var processed: Long               = 0
      val queue: mutable.Queue[JsValue] = mutable.Queue.empty
      var scrollId: Future[String]      = firstResults.map(j => (j \ "_scroll_id").as[String])
      var firstResultProcessed          = false

      setHandler(
        out,
        new OutHandler {

          def pushNextHit(): Unit = {
            push(out, queue.dequeue())
            processed += 1
          }

          val firstCallback: AsyncCallback[Try[JsValue]] = getAsyncCallback[Try[JsValue]] {
            case Success(searchResponse) =>
              queue ++= readHits(searchResponse)
              firstResultProcessed = true
              onPull()
            case Failure(error) =>
              logger.warn("Search error", error)
              failStage(error)
          }

          override def onPull(): Unit =
            if (firstResultProcessed)
              if (queue.isEmpty) {
                val callback = getAsyncCallback[Try[JsValue]] {
                  case Success(searchResponse) =>
                    if ((searchResponse \ "timed_out").as[Boolean]) {
                      logger.warn("Search timeout")
                      failStage(SearchError("Request terminated early or timed out"))
                    } else {
                      val hits = readHits(searchResponse)
                      if (hits.isEmpty) completeStage()
                      else {
                        queue ++= hits
                        pushNextHit()
                      }
                    }
                  case Failure(error) =>
                    logger.warn("Search error", error)
                    failStage(SearchError("Request terminated early or timed out"))
                }
                val futureSearchResponse = scrollId
                  .flatMap(s => client.scroll(s, keepAliveStr))
                scrollId = futureSearchResponse.map(j => (j \ "_scroll_id").as[String])
                futureSearchResponse.onComplete(callback.invoke)
              } else
                pushNextHit()
            else firstResults.onComplete(firstCallback.invoke)
        }
      )
    }
}
