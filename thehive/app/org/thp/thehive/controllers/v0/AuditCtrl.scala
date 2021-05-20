package org.thp.thehive.controllers.v0

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.softwaremill.tagging.@@
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.{Audit, RichAudit}
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class AuditCtrl(
    override val entrypoint: Entrypoint,
    auditSrv: AuditSrv,
    flowActor: ActorRef @@ FlowTag,
    override val publicData: PublicAudit,
    implicit override val db: Database,
    implicit val ec: ExecutionContext,
    override val queryExecutor: QueryExecutor
) extends AuditRenderer
    with QueryCtrl
    with TheHiveOpsNoDeps {
  implicit val timeout: Timeout = Timeout(5.minutes)

  def flow(caseId: Option[String]): Action[AnyContent] =
    entrypoint("audit flow")
      .asyncAuth { implicit request =>
        (flowActor ? FlowId(caseId.filterNot(_ == "any").map(EntityIdOrName(_)))).map {
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
                          "summary" -> JsObject.empty //jsonSummary(auditSrv, audit.requestId)
                        )
                      )
                }
                .toBuffer
            }
            Results.Ok(JsArray(audits))
        }
      }
}

class PublicAudit(auditSrv: AuditSrv, val organisationSrv: OrganisationSrv, val customFieldSrv: CustomFieldSrv, db: Database)
    extends PublicData
    with TheHiveOps {
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Audit]](
    "getAudit",
    (idOrName, graph, authContext) => auditSrv.get(idOrName)(graph).visible(authContext)
  )

  override val entityName: String = "audit"

  override val initialQuery: Query =
    Query.init[Traversal.V[Audit]]("listAudit", (graph, authContext) => auditSrv.startTraversal(graph).visible(authContext))

  override val pageQuery: ParamQuery[org.thp.thehive.controllers.v0.OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Audit], IteratorOutput](
      "page",
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, withTotal = true)(_.richAudit)
    )
  override val outputQuery: Query = Query.output[RichAudit, Traversal.V[Audit]](_.richAudit)

  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Audit]
      .property("operation", UMapping.string)(_.rename("action").readonly)
      .property("details", UMapping.string)(_.field.readonly)
      .property("objectType", UMapping.string.optional)(_.field.readonly)
      .property("objectId", UMapping.string.optional)(_.field.readonly)
      .property("base", UMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UMapping.string)(_.field.readonly)
      .property("rootId", db.idMapping)(_.select(_.context._id).readonly)
      .build
}
