package org.thp.thehive.controllers.v1

import scala.util.Success

import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.RichAudit
import org.thp.thehive.services.{AuditSrv, AuditSteps, LogSteps}

@Singleton
class AuditCtrl @Inject() (entrypoint: Entrypoint, db: Database, properties: Properties, auditSrv: AuditSrv) extends QueryableCtrl {

  val entityName: String = "audit"

  val initialQuery: Query =
    Query.init[AuditSteps]("listAudit", (graph, authContext) => auditSrv.initSteps(graph).visible(authContext))
  val publicProperties: List[PublicProperty[_, _]] = properties.audit ::: metaProperties[LogSteps]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AuditSteps](
    "getAudit",
    FieldsParser[IdOrName],
    (param, graph, authContext) => auditSrv.get(param.idOrName)(graph).visible(authContext)
  )

  val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, AuditSteps, PagedResult[RichAudit]](
      "page",
      FieldsParser[OutputParam],
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, withTotal = true)(_.richAudit)
    )
  val outputQuery: Query = Query.output[RichAudit]()
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[AuditSteps, List[RichAudit]]("toList", (auditSteps, _) => auditSteps.richAudit.toList)
  )

  def flow(): Action[AnyContent] =
    entrypoint("audit flow")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val audits = auditSrv
          .initSteps
          .visible
          .range(0, 10)
          .richAudit
          .toList
          .map(_.toJson)
        Success(Results.Ok(JsArray(audits)))
      }
}
