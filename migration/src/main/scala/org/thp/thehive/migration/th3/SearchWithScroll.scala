package org.thp.thehive.migration.th3

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import org.thp.scalligraph.SearchError
import play.api.Logger
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class SearchWithScroll(client: ElasticClient, docType: String, query: JsObject, keepAliveStr: String)(implicit
    ec: ExecutionContext
) extends GraphStage[SourceShape[JsValue]] {

  private[SearchWithScroll] lazy val logger = Logger(getClass)
  val out: Outlet[JsValue]                  = Outlet[JsValue]("searchHits")
  val shape: SourceShape[JsValue]           = SourceShape.of(out)

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
    new GraphStageLogic(shape) with OutHandler {
      val queue: mutable.Queue[JsValue] = mutable.Queue.empty
      var scrollId: Option[String]      = None
      setHandler(out, this)

      val callback: Try[JsValue] => Unit =
        getAsyncCallback[Try[JsValue]] {
          case Success(searchResponse) =>
            if ((searchResponse \ "timed_out").asOpt[Boolean].contains(true)) {
              logger.warn(s"Search timeout ($docType)")
              failStage(SearchError(s"Request terminated early or timed out ($docType)"))
            } else {
              scrollId = (searchResponse \ "_scroll_id").asOpt[String].orElse(scrollId)
              val hits = readHits(searchResponse)
              if (hits.isEmpty) completeStage()
              else {
                queue ++= hits
                push(out, queue.dequeue())
              }
            }
          case Failure(error) =>
            logger.warn(s"Search error ($docType)", error)
            failStage(SearchError(s"Request terminated early or timed out ($docType)"))
        }.invoke _

      override def onPull(): Unit =
        if (queue.nonEmpty)
          push(out, queue.dequeue())
        else
          scrollId.fold(client.search(docType, query, "scroll" -> keepAliveStr).onComplete(callback)) { sid =>
            client.scroll(sid, keepAliveStr).onComplete(callback)
          }

      override def postStop(): Unit = scrollId.foreach(client.clearScroll(_))
    }
}
