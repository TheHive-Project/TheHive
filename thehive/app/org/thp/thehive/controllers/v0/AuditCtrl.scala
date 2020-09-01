package org.thp.thehive.controllers.v0

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.query.{ParamQuery, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.{Audit, RichAudit}
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.FlowActor.{AuditIds, FlowId}
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

@Singleton
class AuditCtrl @Inject() (
    entryPoint: Entrypoint,
    properties: Properties,
    auditSrv: AuditSrv,
    @Named("flow-actor") flowActor: ActorRef,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    @Named("with-thehive-schema") implicit val db: Database,
    implicit val schema: Schema,
    implicit val ec: ExecutionContext
) extends QueryableCtrl
    with AuditRenderer {

  implicit val timeout: Timeout = Timeout(5.minutes)

  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Audit]](
    "getAudit",
    FieldsParser[IdOrName],
    (param, graph, authContext) => auditSrv.get(param.idOrName)(graph).visible(authContext)
  )

  override val entityName: String = "audit"

  override val initialQuery: Query =
    Query.init[Traversal.V[Audit]]("listAudit", (graph, authContext) => auditSrv.startTraversal(graph).visible(authContext))
  override val publicProperties: List[org.thp.scalligraph.query.PublicProperty[_, _]] = properties.audit

  override val pageQuery: ParamQuery[org.thp.thehive.controllers.v0.OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Audit], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, withTotal = true)(_.richAudit)
    )
  override val outputQuery: Query = Query.output[RichAudit, Traversal.V[Audit]](_.richAudit)

  def flow(caseId: Option[String]): Action[AnyContent] =
    entryPoint("audit flow")
      .asyncAuth { implicit request =>
        (flowActor ? FlowId(request.organisation, caseId.filterNot(_ == "any"))).map {
          case AuditIds(auditIds) if auditIds.isEmpty => Results.Ok(JsArray.empty)
          case AuditIds(auditIds) =>
            val audits = db.roTransaction { implicit graph =>
              auditSrv
                .getByIds(auditIds: _*)
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
                .toBuffer
            }
            Results.Ok(JsArray(audits))
        }
      }
}
