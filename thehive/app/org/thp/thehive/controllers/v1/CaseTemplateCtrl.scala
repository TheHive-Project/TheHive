package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputCaseTemplate
import org.thp.thehive.models.{Permissions, RichCaseTemplate}
import org.thp.thehive.services.{CaseTemplateSrv, CaseTemplateSteps, OrganisationSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class CaseTemplateCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {

  override val entityName: String                           = "caseTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = properties.caseTemplate ::: metaProperties[CaseTemplateSteps]
  override val initialQuery: Query =
    Query.init[CaseTemplateSteps]("listCaseTemplate", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).caseTemplates)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, CaseTemplateSteps](
    "getCaseTemplate",
    FieldsParser[IdOrName],
    (param, graph, authContext) => caseTemplateSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, CaseTemplateSteps, PagedResult[RichCaseTemplate]](
    "page",
    FieldsParser[OutputParam],
    (range, caseTemplateSteps, _) => caseTemplateSteps.richPage(range.from, range.to, range.extraData.contains("total"))(_.richCaseTemplate)
  )
  override val outputQuery: Query               = Query.output[RichCaseTemplate, CaseTemplateSteps](_.richCaseTemplate)
  override val extraQueries: Seq[ParamQuery[_]] = Seq()

  def create: Action[AnyContent] =
    entrypoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        for {
          organisation <- organisationSrv.getOrFail(request.organisation)
          tasks        = inputCaseTemplate.tasks.map(_.toTask -> None)
          customFields = inputCaseTemplate.customFieldValue.map(cf => cf.name -> cf.value)
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.toCaseTemplate, organisation, inputCaseTemplate.tags, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(caseTemplateNameOrId)
          .visible
          .richCaseTemplate
          .getOrFail("CaseTemplate")
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val caseTemplates = caseTemplateSrv
          .initSteps
          .visible
          .richCaseTemplate
          .toList
        Success(Results.Ok(caseTemplates.toJson))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entrypoint("update case template")
      .extract("caseTemplate", FieldsParser.update("caseTemplate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("caseTemplate")
        caseTemplateSrv
          .update(
            _.get(caseTemplateNameOrId)
              .can(Permissions.manageCaseTemplate),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
