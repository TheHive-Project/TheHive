package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, Query}
import org.thp.thehive.dto.v0.OutputAudit
import org.thp.thehive.models.RichAudit
import org.thp.thehive.services._

@Singleton
class AuditCtrl @Inject()(
    entryPoint: EntryPoint,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database
) extends QueryableCtrl {
  import AuditConversion._

  val entityName: String = "audit"

  val initialQuery: org.thp.scalligraph.query.Query =
    Query.init[AuditSteps]("listAudit", (graph, authContext) => auditSrv.initSteps(graph).visible(authContext))
  val publicProperties: List[org.thp.scalligraph.query.PublicProperty[_, _]] = auditProperties ::: metaProperties[LogSteps]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AuditSteps](
    "getAudit",
    FieldsParser[IdOrName],
    (param, graph, authContext) => auditSrv.get(param.idOrName)(graph).visible(authContext)
  )

  val pageQuery: org.thp.scalligraph.query.ParamQuery[org.thp.thehive.controllers.v0.OutputParam] =
    Query.withParam[OutputParam, AuditSteps, PagedResult[RichAudit]](
      "page",
      FieldsParser[OutputParam],
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, withTotal = true)(_.richAudit.raw)
    )
  val outputQuery: org.thp.scalligraph.query.Query = Query.output[RichAudit, OutputAudit]
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[AuditSteps, List[RichAudit]]("toList", (auditSteps, _) => auditSteps.richAudit.toList)
  )

  def flow(caseId: Option[String], count: Option[Int]): Action[AnyContent] =
    entryPoint("audit flow")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val auditTraversal = auditSrv.initSteps.has(Key("mainAction"), P.eq(true))
        val audits = caseId
          .filterNot(_ == "any")
          .fold(auditTraversal)(cid => auditTraversal.forCase(cid))
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
