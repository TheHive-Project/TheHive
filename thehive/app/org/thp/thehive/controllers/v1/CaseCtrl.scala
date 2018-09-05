package org.thp.thehive.controllers.v1

import java.util.Date

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.{Database, Output}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, UserSrv}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

case class InputCase(
    title: String,
    description: String,
    severity: Option[Int],
    startDate: Option[Date],
    endDate: Option[Date],
    tags: Seq[String],
    flag: Option[Boolean],
    tlp: Option[Int],
    pap: Option[Int],
    status: Option[CaseStatus.Value],
    summary: Option[String]) {

  def toCase: Case =
    this
      .into[Case]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, CaseStatus.open)
      .withFieldConst(_.number, 0)
      .transform
}

case class OutputCase(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    number: Int,
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date],
    tags: Seq[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: CaseStatus.Value,
    summary: Option[String],
    user: String,
    customFields: Seq[CustomFieldValue])

object OutputCase {
  def fromRichCase(richCase: RichCase): OutputCase =
    richCase
      .into[OutputCase]
      .transform
  implicit val writes: Writes[OutputCase] = Output[OutputCase]
}

@Singleton
class CaseCtrl @Inject()(apiMethod: ApiMethod, db: Database, caseSrv: CaseSrv, userSrv: UserSrv) {

  def create: Action[AnyContent] =
    apiMethod("create case")
      .extract('case, FieldsParser[InputCase])
      .extract('customFields, CustomField.parser.on("customFields"))
      .extract('user, FieldsParser.string.optional.on("user"))
      .requires(Permissions.write) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val userId: String = request.body('user).getOrElse(request.userId)
          val user           = userSrv.getOrFail(userId)
          val customFields   = request.body('customFields)
          val richCase       = caseSrv.create(request.body('case).toCase, user, customFields)
          val outputCase     = OutputCase.fromRichCase(richCase)
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
          val outputCase = OutputCase.fromRichCase(richCase)
          Results.Ok(Json.toJson(outputCase))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list case")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val cases = caseSrv.steps.richCase
            .map(OutputCase.fromRichCase)
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
