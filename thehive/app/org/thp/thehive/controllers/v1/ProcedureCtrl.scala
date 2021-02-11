package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputProcedure
import org.thp.thehive.models.{Permissions, Procedure, RichProcedure}
import org.thp.thehive.services.ProcedureOps._
import org.thp.thehive.services.ProcedureSrv
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Named, Singleton}

@Singleton
class ProcedureCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    procedureSrv: ProcedureSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl
    with ProcedureRenderer {
  override val entityName: String                 = "procedure"
  override val publicProperties: PublicProperties = properties.procedure
  override val initialQuery: Query = Query.init[Traversal.V[Procedure]](
    "listProcedure",
    (graph, _) =>
      procedureSrv
        .startTraversal(graph)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Procedure], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, procedureSteps, _) =>
      procedureSteps.richPage(range.from, range.to, range.extraData.contains("total"))(
        _.richProcedureWithCustomRenderer(procedureStatsRenderer(range.extraData - "total"))
      )
  )
  override val outputQuery: Query = Query.output[RichProcedure, Traversal.V[Procedure]](_.richProcedure)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Procedure]](
    "getProcedure",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, _) => procedureSrv.get(idOrName)(graph)
  )

  def create: Action[AnyContent] =
    entrypoint("create procedure")
      .extract("procedure", FieldsParser[InputProcedure])
      .authPermittedTransaction(db, Permissions.manageProcedure) { implicit request => implicit graph =>
        val inputProcedure: InputProcedure = request.body("procedure")
        for {
          richProcedure <- procedureSrv.create(inputProcedure.toProcedure, inputProcedure.caseId, inputProcedure.patternId)
        } yield Results.Created(richProcedure.toJson)
      }

  def get(procedureId: String): Action[AnyContent] =
    entrypoint("get procedure")
      .authRoTransaction(db) { _ => implicit graph =>
        procedureSrv
          .get(EntityIdOrName(procedureId))
          .richProcedure
          .getOrFail("Procedure")
          .map(richProcedure => Results.Ok(richProcedure.toJson))
      }

  def update(procedureId: String): Action[AnyContent] =
    entrypoint("update procedure")
      .extract("procedure", FieldsParser.update("procedure", properties.procedure))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("procedure")
        procedureSrv
          .update(
            _.get(EntityIdOrName(procedureId)),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def delete(procedureId: String): Action[AnyContent] =
    entrypoint("delete procedure")
      .authPermittedTransaction(db, Permissions.manageProcedure) { implicit request => implicit graph =>
        procedureSrv
          .getOrFail(EntityIdOrName(procedureId))
          .flatMap(procedureSrv.remove)
          .map(_ => Results.NoContent)
      }

}
