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
      val queue: mutable.Queue[JsValue] = mutable.Queue.empty
      var scrollId: Option[String]      = None
      var firstResultProcessed          = false

      setHandler(
        out,
        new OutHandler {

          def firstCallback: AsyncCallback[Try[JsValue]] =
            getAsyncCallback[Try[JsValue]] {
              case Success(searchResponse) =>
                val hits = readHits(searchResponse)
                if (hits.isEmpty)
                  completeStage()
                else {
                  queue ++= hits
                  scrollId = (searchResponse \ "_scroll_id").asOpt[String].orElse(scrollId)
                  firstResultProcessed = true
                  push(out, queue.dequeue())
                }
              case Failure(error) =>
                logger.warn("Search error", error)
                failStage(error)
            }

          def callback: AsyncCallback[Try[JsValue]] =
            getAsyncCallback[Try[JsValue]] {
              case Success(searchResponse) =>
                scrollId = (searchResponse \ "_scroll_id").asOpt[String].orElse(scrollId)
                if ((searchResponse \ "timed_out").as[Boolean]) {
                  logger.warn(s"Search timeout")
                  failStage(SearchError(s"Request terminated early or timed out ($docType)"))
                } else {
                  val hits = readHits(searchResponse)
                  if (hits.isEmpty)
                    completeStage()
                  else {
                    queue ++= hits
                    push(out, queue.dequeue())
                  }
                }
              case Failure(error) =>
                logger.warn(s"Search error", error)
                failStage(SearchError(s"Request terminated early or timed out"))
            }

          override def onPull(): Unit =
            if (firstResultProcessed)
              if (queue.isEmpty) client.scroll(scrollId.get, keepAliveStr).onComplete(callback.invoke)
              else push(out, queue.dequeue())
            else firstResults.onComplete(firstCallback.invoke)
        }
      )

      override def postStop(): Unit =
        scrollId.foreach(client.clearScroll(_))
    }
}
