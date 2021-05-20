package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.auth.{ExpirationStatus, SessionAuthSrv}
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class StreamCtrl(
    entrypoint: Entrypoint,
    streamSrv: StreamSrv,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database,
    implicit val schema: Schema,
    implicit val ec: ExecutionContext
) extends AuditRenderer {

  def create: Action[AnyContent] =
    entrypoint("create stream")
      .auth { implicit request =>
        val streamId = streamSrv.create
        Success(Results.Ok(streamId))
      }

  def get(streamId: String): Action[AnyContent] =
    entrypoint("get stream").async { request =>
      if (SessionAuthSrv.isExpired(request))
        Future.successful(Results.Unauthorized)
      else
        streamSrv
          .get(streamId)
          .map {
            case auditIds if auditIds.nonEmpty =>
              db.roTransaction { implicit graph =>
                val audits = auditSrv
                  .getMainByIds(Order.desc, auditIds: _*)
                  .richAuditWithCustomRenderer(auditRenderer)
                  .toIterator
                  .map {
                    case (audit, obj) =>
                      audit
                        .toJson
                        .as[JsObject]
                        .deepMerge(
                          Json.obj(
                            "base"    -> Json.obj("object" -> obj, "rootId" -> audit.context._id),
                            "summary" -> jsonSummary(auditSrv, audit.requestId)
                          )
                        )
                  }
                if (SessionAuthSrv.isWarning(request))
                  new Results.Status(220)(JsArray(audits.toSeq))
                else
                  Results.Ok(JsArray(audits.toSeq))
              }
            case _ if SessionAuthSrv.isWarning(request) => new Results.Status(220)(JsArray.empty)
            case _                                      => Results.Ok(JsArray.empty)
          }
    }

  def status: Action[AnyContent] =
    entrypoint("get stream") { request =>
      val status = SessionAuthSrv.expirationStatus(request) match {
        case Some(ExpirationStatus.Ok(remaining))      => Json.obj("warning" -> false, "remaining" -> remaining.toMillis)
        case Some(ExpirationStatus.Warning(remaining)) => Json.obj("warning" -> true, "remaining" -> remaining.toMillis)
        case Some(ExpirationStatus.Error)              => Json.obj("warning" -> true, "remaining" -> 0)
        case None                                      => Json.obj("warning" -> false, "remaining" -> 1)
      }
      Success(Results.Ok(status))
    }
}
