package controllers

import scala.concurrent.{ ExecutionContext, Future }

import play.api.http.Status
import play.api.libs.json.{ JsNumber, JsObject }
import play.api.mvc.{ AbstractController, Action, AnyContent, ControllerComponents }

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.http.ElasticDsl.{ search, termsAggregation }
import javax.inject.{ Inject, Singleton }
import models.Roles

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Authenticated
import org.elastic4play.database.DBFind
import org.elastic4play.services.DBLists
import org.elastic4play.services.QueryDSL._

@Singleton
class CustomFieldsCtrl @Inject()(
    authenticated: Authenticated,
    dbfind: DBFind,
    dblists: DBLists,
    components: ControllerComponents,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends AbstractController(components)
    with Status {

  def useCount(customField: String): Action[AnyContent] =
    authenticated(Roles.read)
      .async {
        dblists("custom_fields")
          .getItems[JsObject]
          ._1
          .collect {
            case (_, value) if (value \ "reference").asOpt[String].contains(customField) ⇒ (value \ "type").as[String]
          }
          .runWith(Sink.head)
          .recoverWith { case _ ⇒ Future.failed(NotFoundError(s"CustomField $customField not found")) }
          .flatMap { customFieldType ⇒
            val filter = and("relations" in ("case", "alert", "caseTemplate"), contains(s"customFields.$customField.$customFieldType"))
            dbfind(
              indexName ⇒ search(indexName).query(filter.query).aggregations(termsAggregation("t").field("relations"))
            ).map { searchResponse ⇒
                val result = JsObject(searchResponse.aggregations.terms("t").buckets.map { b ⇒
                  b.key → JsNumber(b.docCount)
                })
                Ok(result)
              }
              .recover { case _ ⇒ Ok(JsObject.empty) }
          }
      }
}
