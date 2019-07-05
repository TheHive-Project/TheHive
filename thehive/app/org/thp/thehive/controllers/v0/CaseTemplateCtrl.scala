package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v0.InputCaseTemplate
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{CaseTemplateSrv, OrganisationSrv, TaskSrv, UserSrv}

@Singleton
class CaseTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseTemplateSrv: CaseTemplateSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    taskSrv: TaskSrv
) {
  import CaseTemplateConversion._
  import TaskConversion._
  import CustomFieldConversion._

  def create: Action[AnyContent] =
    entryPoint("create case template")
      .extract("caseTemplate", FieldsParser[InputCaseTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputCaseTemplate: InputCaseTemplate = request.body("caseTemplate")
        for {
          organisation <- organisationSrv.getOrFail(request.organisation)
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

  def list: Action[AnyContent] =
    entryPoint("list case template")
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplates = caseTemplateSrv
          .initSteps
          .visible
          .richCaseTemplate
          .map(_.toJson)
          .toList
        Success(Results.Ok(Json.toJson(caseTemplates)))
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

  def search: Action[AnyContent] = list
  //  {
  //    entryPoint("search case template")
  //      //.extract("query", )
  //      .authenticated { implicit request =>
  //      db.transaction { implicit graph =>
  //        caseTemplateSrv.initSteps.toL
  //      }
  //    }
  //  }
}
