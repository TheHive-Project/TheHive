package org.thp.thehive.controllers.v0

import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputCaseTemplate
import org.thp.thehive.models.{Permissions, RichCaseTemplate}
import org.thp.thehive.services._

@Singleton
class CaseTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    auditSrv: AuditSrv
) extends QueryableCtrl {

  lazy val logger                                           = Logger(getClass)
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
    (range, caseTemplateSteps, _) => caseTemplateSteps.richPage(range.from, range.to, withTotal = true)(_.richCaseTemplate)
  )
  override val outputQuery: Query = Query.output[RichCaseTemplate]()
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[CaseTemplateSteps, List[RichCaseTemplate]]("toList", (caseTemplateSteps, _) => caseTemplateSteps.richCaseTemplate.toList)
  )

  def create: Action[AnyContent] =
    entryPoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        val tasks                                = inputCaseTemplate.tasks.map(_.toTask)
        val customFields                         = inputCaseTemplate.customFields.map(c => c.name -> c.value)
        for {
          organisation     <- userSrv.current.organisations(Permissions.manageCaseTemplate).get(request.organisation).getOrFail()
          richCaseTemplate <- caseTemplateSrv.create(inputCaseTemplate.toCaseTemplate, organisation, inputCaseTemplate.tags, tasks, customFields)
        } yield Results.Created(richCaseTemplate.toJson)
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("get case template")
      .authRoTransaction(db) { implicit request => implicit graph =>
        caseTemplateSrv
          .get(caseTemplateNameOrId)
          .visible
          .richCaseTemplate
          .getOrFail()
          .map(richCaseTemplate => Results.Ok(richCaseTemplate.toJson))
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("update case template")
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

  def delete(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("delete case template")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          template <- caseTemplateSrv.get(caseTemplateNameOrId).can(Permissions.manageCaseTemplate).getOrFail()
          _ = caseTemplateSrv.get(template).remove()
          _ <- auditSrv.caseTemplate.delete(template)
        } yield Results.Ok
      }
}
