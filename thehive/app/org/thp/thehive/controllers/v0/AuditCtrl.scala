package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AuditCtrl @Inject()(
    entryPoint: EntryPoint,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database
) {
  import AuditConversion._

  def flow(caseId: Option[String], count: Option[Int]): Action[AnyContent] =
    entryPoint("audit flow")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val audits = caseId
          .filterNot(_ == "any")
          .fold(auditSrv.initSteps)(rid => auditSrv.initSteps.forCase(rid))
          .visible
          .range(0, count.getOrElse(10).toLong)
          .richAuditWithCustomRenderer(auditRenderer)
          .toList
          .map {
            case (audit, (rootId, obj)) =>
              audit.toJson.as[JsObject].deepMerge(Json.obj("base" -> Json.obj("object" -> obj, "rootId" -> rootId)))
          }

        Success(Results.Ok(JsArray(audits)))
      }
}
