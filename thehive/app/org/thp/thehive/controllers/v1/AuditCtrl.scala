package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, Query}
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{Audit, RichAudit}
import org.thp.thehive.services.{AuditSrv, CustomFieldSrv, CustomFieldValueSrv, OrganisationSrv, TheHiveOps}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class AuditCtrl(
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    auditSrv: AuditSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    implicit val schema: Schema
) extends QueryableCtrl
    with TheHiveOps {

  val entityName: String = "audit"

  val initialQuery: Query =
    Query.init[Traversal.V[Audit]]("listAudit", (graph, authContext) => auditSrv.startTraversal(graph).visible(authContext))
  val publicProperties: PublicProperties = properties.audit
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Audit]](
    "getAudit",
    (idOrName, graph, authContext) => auditSrv.get(idOrName)(graph).visible(authContext)
  )

  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Audit], IteratorOutput](
      "page",
      (range, auditSteps, _) => auditSteps.richPage(range.from, range.to, range.extraData.contains("total"), limitedCountThreshold)(_.richAudit)
    )
  override val outputQuery: Query = Query.output[RichAudit, Traversal.V[Audit]](_.richAudit)

  override val extraQueries: Seq[ParamQuery[_]] = {
    implicit val entityIdParser: FieldsParser[String] = FieldsParser.string.on("id")
    Seq(
      Query.initWithParam[String, Traversal.V[Audit]](
        "listAuditFromObject",
        (objectId, graph, authContext) =>
          if (auditSrv.startTraversal(graph).has(_.objectId, objectId).v[Audit].limit(1).visible(organisationSrv)(authContext).exists)
            auditSrv.startTraversal(graph).has(_.objectId, objectId).v[Audit]
          else
            graph.empty
      )
    )
  }

  def flow: Action[AnyContent] =
    entrypoint("audit flow")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val audits = auditSrv
          .startTraversal
          .visible
          .range(0, 10)
          .richAudit
          .toSeq

        Success(Results.Ok(audits.toJson))
      }
}
