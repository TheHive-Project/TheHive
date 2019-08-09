package org.thp.thehive.controllers.v0

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}
import scala.concurrent.ExecutionContext
import scala.util.Success

import gremlin.scala.{Key, P}

@Singleton
class StreamCtrl @Inject()(
    entryPoint: EntryPoint,
    streamSrv: StreamSrv,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database,
    implicit val ec: ExecutionContext,
    system: ActorSystem
) {
  import AuditConversion._

  def create: Action[AnyContent] =
    entryPoint("create stream")
      .auth { implicit request =>
        val streamId = streamSrv.create
        Success(Results.Ok(streamId))
      }

  def get(streamId: String): Action[AnyContent] =
    entryPoint("get stream").async { _ =>
      streamSrv
        .get(streamId)
        .map {
          case auditIds if auditIds.nonEmpty =>
            db.roTransaction { implicit graph =>
              val audits = auditSrv
                .getByIds(auditIds: _*)
                .has(Key("mainAction"), P.eq(true))
                .richAuditWithCustomRenderer(auditRenderer)
                .toIterator
                .map {
                  case (audit, (rootId, obj)) =>
                    audit.toJson.as[JsObject].deepMerge(Json.obj("base" -> Json.obj("object" -> obj, "rootId" -> rootId)))
                }
              Results.Ok(JsArray(audits.toSeq))
            }
          case _ => Results.Ok(JsArray.empty)
        }
    }

  def status: Action[AnyContent] = // TODO
    entryPoint("get stream") { _ =>
      Success(
        Results.Ok(
          Json.obj(
            "remaining" -> 3600,
            "warning"   -> false
          )
        )
      )
    }
}
