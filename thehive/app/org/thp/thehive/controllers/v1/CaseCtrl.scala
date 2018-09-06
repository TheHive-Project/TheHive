package org.thp.thehive.controllers.v1

import java.util.Date

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, UserSrv}

object CaseXfrm {
  def fromInput(inputCase: InputCase): Case =
    inputCase
      .into[Case]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, CaseStatus.open)
      .withFieldConst(_.number, 0)
      .transform

  def toOutput(richCase: RichCase): OutputCase =
    richCase
      .into[OutputCase]
      .withFieldComputed(_.customFields, _.customFields.map(CustomFieldXfrm.toOutput).toSet)
      .withFieldComputed(_.status, _.status.toString)
      .transform
}
@Singleton
class CaseCtrl @Inject()(apiMethod: ApiMethod, db: Database, caseSrv: CaseSrv, userSrv: UserSrv) {

  def create: Action[AnyContent] =
    apiMethod("create case")
      .extract('case, FieldsParser[InputCase])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputCase: InputCase = request.body('case)
          val user                 = userSrv.getOrFail(inputCase.user.getOrElse(request.userId))
          val customFields         = inputCase.customFieldValue.map(CustomFieldXfrm.fromInput)
          val richCase             = caseSrv.create(CaseXfrm.fromInput(request.body('case)), user, customFields)
          val outputCase           = CaseXfrm.toOutput(richCase)
          Results.Created(Json.toJson(outputCase))
        }
      }

  def get(caseIdOrNumber: String): Action[AnyContent] =
    apiMethod("get case")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val richCase = caseSrv
            .get(caseIdOrNumber)
            .richCase
            .headOption
            .getOrElse(throw NotFoundError(s"case number $caseIdOrNumber not found"))
          val outputCase = CaseXfrm.toOutput(richCase)
          Results.Ok(Json.toJson(outputCase))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list case")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val cases = caseSrv.steps.richCase
            .map(CaseXfrm.toOutput)
            .toList
          Results.Ok(Json.toJson(cases))
        }
      }

  def update(caseIdOrNumber: String): Action[AnyContent] =
    apiMethod("update case")
      .extract('case, UpdateFieldsParser[InputCase])
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val caseId = caseSrv.getOrFail(caseIdOrNumber)._id
          caseSrv.update(caseId, request.body('case))
          Results.NoContent
        }
      }
}
