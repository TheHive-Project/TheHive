package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputCase
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class CaseCtrl @Inject()(
    apiMethod: ApiMethod,
    db: Database,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv) {

  def create: Action[AnyContent] =
    apiMethod("create case")
      .extract('case, FieldsParser[InputCase])
      .extract('caseTemplate, FieldsParser[String].optional.on("caseTemplate"))
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val caseTemplateName: Option[String] = request.body('caseTemplate)
          val caseTemplate = caseTemplateName
            .map { templateName ⇒
              caseTemplateSrv
                .get(templateName)
                .availableFor(request.organisation)
                .richCaseTemplate
                .getOrFail()
            }
          val inputCase: InputCase = request.body('case)
          val `case`               = fromInputCase(inputCase, caseTemplate)
          val user                 = userSrv.getOrFail(inputCase.user.getOrElse(request.userId))
          val organisation         = organisationSrv.getOrFail(request.organisation)
          val customFields         = inputCase.customFieldValue.map(fromInputCustomField).toMap
          val richCase             = caseSrv.create(`case`, user, organisation, customFields, caseTemplate)

          caseTemplate.foreach { ct ⇒
            caseTemplateSrv.get(ct.caseTemplate).tasks.toList().foreach { task ⇒
              taskSrv.create(task, richCase.`case`)
            }
          }
          Results.Created(richCase.toJson)
        }
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    apiMethod("get case")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val richCase = caseSrv
            .get(caseIdOrNumber)
            .availableFor(request.organisation)
            .richCase
            .getOrFail()
          Results.Ok(richCase.toJson)
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list case")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val cases = caseSrv.initSteps
            .availableFor(request.organisation)
            .richCase
            .map(_.toJson)
            .toList()
          Results.Ok(Json.toJson(cases))
        }
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    apiMethod("update case")
      .extract('case, UpdateFieldsParser[InputCase])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          if (caseSrv.isAvailableFor(caseIdOrNumber)) {
            caseSrv.update(caseIdOrNumber, outputCaseProperties(db), request.body('case))
            Results.NoContent
          } else Results.Unauthorized(s"Case $caseIdOrNumber doesn't exist or permission is insufficient")
        }
      }

  def merge(caseIdsOrNumbers: String): Action[AnyContent] =
    apiMethod("merge cases")
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val cases = caseIdsOrNumbers
            .split(',')
            .map { i ⇒
              caseSrv
                .get(i)
                .availableFor(request.organisation)
                .getOrFail()
            }
          val mergedCase = caseSrv.merge(cases)
          Results.Ok(mergedCase.toJson)
        }
      }
}
