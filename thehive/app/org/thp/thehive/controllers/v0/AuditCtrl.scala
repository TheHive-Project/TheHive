package org.thp.thehive.controllers.v0

import java.util.Date

import gremlin.scala.{By, Key}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.RichAudit
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AuditCtrl @Inject() (
    entryPoint: Entrypoint,
    properties: Properties,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database
) extends QueryableCtrl
    with AuditRenderer {

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AuditSteps](
    "getAudit",
    FieldsParser[IdOrName],
    (param, graph, authContext) => auditSrv.get(param.idOrName)(graph).visible(authContext)
  )

  override val entityName: String = "audit"

  override val initialQuery: Query =
    Query.init[AuditSteps]("listAudit", (graph, authContext) => auditSrv.initSteps(graph).visible(authContext))
  override val publicProperties: List[org.thp.scalligraph.query.PublicProperty[_, _]] = properties.audit ::: metaProperties[LogSteps]

  override val pageQuery: ParamQuery[org.thp.thehive.controllers.v0.OutputParam] =
    Query.withParam[OutputParam, AuditSteps, PagedResult[RichAudit]](
      "page",
      FieldsParser[OutputParam],
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, withTotal = true)(_.richAudit)
    )
  override val outputQuery: Query = Query.output[RichAudit, AuditSteps](_.richAudit)

  def flow(caseId: Option[String], count: Option[Int]): Action[AnyContent] =
    entryPoint("audit flow")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val auditTraversal: AuditSteps = auditSrv.initSteps.has("mainAction", true)
        val audits = caseId
          .filterNot(_ == "any")
          .fold(auditTraversal)(cid => auditTraversal.forCase(cid))
          .visible
          .order(List(By(Key[Date]("_createdAt"), Order.desc)))
          .range(0, count.getOrElse(10).toLong)
          .richAuditWithCustomRenderer(auditRenderer)
          .toIterator
          .map {
            case (audit, obj) =>
              audit
                .toJson
                .as[JsObject]
                .deepMerge(
                  Json.obj("base" -> Json.obj("object" -> obj, "rootId" -> audit.context._id), "summary" -> jsonSummary(auditSrv, audit.requestId))
                )
          }
          .toSeq

        Success(Results.Ok(JsArray(audits)))
      }
}
