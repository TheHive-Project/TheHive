package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputCase
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, UserSrv}

@Singleton
class CaseCtrl @Inject()(apiMethod: ApiMethod, db: Database, caseSrv: CaseSrv, userSrv: UserSrv) {

  def create: Action[AnyContent] =
    apiMethod("create case")
      .extract('case, FieldsParser[InputCase])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputCase: InputCase = request.body('case)
          val user                 = userSrv.getOrFail(inputCase.user.getOrElse(request.userId))
          val organisation         = userSrv.getOrganisation(user)
          val customFields         = inputCase.customFieldValue.map(CustomFieldXfrm.fromInput)
          val richCase             = caseSrv.create(request.body('case), user, organisation, customFields)
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
            .headOption
            .getOrElse(throw NotFoundError(s"case number $caseIdOrNumber not found"))
          if (richCase.organisation != request.organisation)
            throw NotFoundError(s"case number $caseIdOrNumber not found")
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
            .toList
          Results.Ok(Json.toJson(cases))
        }
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
//    apiMethod("update case")
//      .extract('case, UpdateFieldsParser[InputCase])
//      .requires(Permissions.write) { implicit request ⇒
//        db.transaction { implicit graph ⇒
//          val caseId = caseSrv.getOrFail(caseIdOrNumber)._id
//          caseSrv.update(caseId, request.body('case))
//          Results.NoContent
//        }
//      }
    ???
}
