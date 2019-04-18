package org.thp.thehive.controllers.v1

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputCaseTemplate
import org.thp.thehive.services.{CaseTemplateSrv, OrganisationSrv, UserSrv}

@Singleton
class CaseTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv)
    extends CaseTemplateConversion
    with TaskConversion
    with CustomFieldConversion {

  def create: Action[AnyContent] =
    entryPoint("create case template")
      .extract('caseTemplate, FieldsParser[InputCaseTemplate])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val inputCaseTemplate: InputCaseTemplate = request.body('caseTemplate)
          for {
            organisation ← organisationSrv.getOrFail(request.organisation)
            tasks        = inputCaseTemplate.tasks.map(fromInputTask)
            customFields = inputCaseTemplate.customFieldValue.map(fromInputCustomField)
            richCaseTemplate ← caseTemplateSrv.create(inputCaseTemplate, organisation, tasks, customFields)
          } yield Results.Created(richCaseTemplate.toJson)
        }
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("get case template")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          caseTemplateSrv
            .get(caseTemplateNameOrId)
            .available
            .richCaseTemplate
            .getOrFail()
            .map(richCaseTemplate ⇒ Results.Ok(richCaseTemplate.toJson))
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list case template")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val caseTemplates = caseTemplateSrv.initSteps.available.richCaseTemplate
            .map(_.toJson)
            .toList()
          Success(Results.Ok(Json.toJson(caseTemplates)))
        }
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    entryPoint("update case template")
      .extract('caseTemplate, UpdateFieldsParser[InputCaseTemplate])
      .authenticated { implicit request ⇒ // FIXME check organization
        db.tryTransaction { implicit graph ⇒
          caseTemplateSrv.update(caseTemplateNameOrId, outputCaseTemplateProperties(db), request.body('caseTemplate))
          Success(Results.NoContent)
        }
      }
}
