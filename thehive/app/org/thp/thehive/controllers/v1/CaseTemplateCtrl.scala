package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{InputCase, InputCaseTemplate}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseTemplateSrv, OrganisationSrv, UserSrv}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class CaseTemplateCtrl @Inject()(
    apiMethod: ApiMethod,
    db: Database,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv) {

  def create: Action[AnyContent] =
    apiMethod("create case template")
      .extract('caseTemplate, FieldsParser[InputCaseTemplate])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputCaseTemplate: InputCaseTemplate = request.body('caseTemplate)
          val organisation                         = organisationSrv.getOrFail(request.organisation)
          val customFields                         = inputCaseTemplate.customFieldValue.map(fromInputCustomField)
          val richCaseTemplate                     = caseTemplateSrv.create(inputCaseTemplate, organisation, customFields)
          Results.Created(richCaseTemplate.toJson)
        }
      }

  def get(caseTemplateNameOrId: String): Action[AnyContent] =
    apiMethod("get case template")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val richCaseTemplate = caseTemplateSrv
            .get(caseTemplateNameOrId)
            .availableFor(request.organisation)
            .richCaseTemplate
            .headOption
            .getOrElse(throw NotFoundError(s"case template $caseTemplateNameOrId not found"))
          Results.Ok(richCaseTemplate.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list case template")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val caseTemplates = caseTemplateSrv.initSteps
            .availableFor(request.organisation)
            .richCaseTemplate
            .map(_.toJson)
            .toList()
          Results.Ok(Json.toJson(caseTemplates))
        }
      }

  def update(caseTemplateNameOrId: String): Action[AnyContent] =
    apiMethod("update case template")
      .extract('caseTemplate, UpdateFieldsParser[InputCaseTemplate])
      .requires(Permissions.write) { implicit request ⇒ // FIXME check organization
        db.transaction { implicit graph ⇒
          caseTemplateSrv.update(caseTemplateNameOrId, outputCaseTemplateProperties(db), request.body('caseTemplate))
          Results.NoContent
        }
      }
}
