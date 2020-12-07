package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputCaseTemplate
import org.thp.thehive.models.{CaseTemplate, Permissions, RichCaseTemplate}
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.{CaseTemplateSrv, OrganisationSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CaseTemplateCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {

  override val entityName: String                 = "caseTemplate"
  override val publicProperties: PublicProperties = properties.caseTemplate
  override val initialQuery: Query =
    Query
      .init[Traversal.V[CaseTemplate]]("listCaseTemplate", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).caseTemplates)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[CaseTemplate]](
    "getCaseTemplate",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => caseTemplateSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[CaseTemplate], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, caseTemplateSteps, authContext) =>
      caseTemplateSteps.richPage(range.from, range.to, range.extraData.contains("total"))(_.richCaseTemplate(authContext))
  )
  override val outputQuery: Query =
    Query.outputWithContext[RichCaseTemplate, Traversal.V[CaseTemplate]]((ctSteps, authContext) => ctSteps.richCaseTemplate(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq()

  def create: Action[AnyContent] =
    entrypoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        for {
          organisation <- organisationSrv.current.getOrFail("Organisation")
          tasks        = inputCaseTemplate.tasks.map(_.toTask -> None)
          customFields = inputCaseTemplate.customFieldValue.map(cf => cf.name -> cf.value)
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.toCaseTemplate, organisation, inputCaseTemplate.tags, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(EntityIdOrName(caseTemplateNameOrId))
          .visible
          .richCaseTemplate
          .getOrFail("CaseTemplate")
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val caseTemplates = caseTemplateSrv
          .startTraversal
          .visible
          .richCaseTemplate
          .toSeq
        Success(Results.Ok(caseTemplates.toJson))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("update case template")
      .extract("caseTemplate", FieldsParser.update("caseTemplate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("caseTemplate")
        caseTemplateSrv
          .update(
            _.get(EntityIdOrName(caseTemplateNameOrId))
              .can(Permissions.manageCaseTemplate),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
