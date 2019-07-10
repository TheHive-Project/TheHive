package org.thp.thehive.controllers.v0

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputCaseTemplate, OutputCaseTemplate}
import org.thp.thehive.models.{Permissions, RichCaseTemplate}
import org.thp.thehive.services.{CaseTemplateSrv, CaseTemplateSteps, OrganisationSrv, UserSrv}

@Singleton
class CaseTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends QueryableCtrl {
  import CaseTemplateConversion._
  import CustomFieldConversion._
  import TaskConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "caseTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = caseTemplateProperties ::: metaProperties[CaseTemplateSteps]
  override val initialQuery: ParamQuery[_] =
    Query.init[CaseTemplateSteps]("listCaseTemplate", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).caseTemplates)
  override val pageQuery: ParamQuery[_] = Query.withParam[OutputParam, CaseTemplateSteps, PagedResult[RichCaseTemplate]](
    "page",
    FieldsParser[OutputParam],
    (range, caseTemplateSteps, _) => caseTemplateSteps.richPage(range.from, range.to, range.withSize.getOrElse(false))(_.richCaseTemplate.raw)
  )
  override val outputQuery: ParamQuery[_] = Query.output[RichCaseTemplate, OutputCaseTemplate]

  def create: Action[AnyContent] =
    entryPoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        for {
          organisation <- userSrv.current.organisations(Permissions.manageCaseTemplate).get(request.organisation).getOrFail()
          tasks        = inputCaseTemplate.tasks.map(fromInputTask)
          customFields = inputCaseTemplate.customFieldValue.map(fromInputCustomField)
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate, organisation, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("get case template")
      .authTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(caseTemplateNameOrId)
          .visible
          .richCaseTemplate
          .getOrFail()
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("update case template")
      .extract("caseTemplate", FieldsParser.update("caseTemplate", caseTemplateProperties))
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
