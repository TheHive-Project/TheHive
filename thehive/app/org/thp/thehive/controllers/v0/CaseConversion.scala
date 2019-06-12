package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseSteps, UserSrv}

trait CaseConversion extends CustomFieldConversion {
  val caseSrv: CaseSrv
  val userSrv: UserSrv

  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case")
        .withFieldComputed(_.id, _._id)
        .withFieldRenamed(_.number, _.caseId)
        .withFieldRenamed(_.user, _.owner)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldConst(_.stats, JsObject.empty)
        .transform
    )

  implicit def fromInputCase(inputCase: InputCase): Case =
    inputCase
      .into[Case]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, CaseStatus.Open)
      .withFieldConst(_.number, 0)
      .transform

  val caseProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseSteps]
      .property[String]("title")(_.simple.updatable)
      .property[String]("description")(_.simple.updatable)
      .property[Int]("severity")(_.simple.updatable)
      .property[Date]("startDate")(_.simple.updatable)
      .property[Option[Date]]("endDate")(_.simple.updatable)
      .property[Set[String]]("tags")(_.simple.updatable)
      .property[Boolean]("flag")(_.simple.updatable)
      .property[Int]("tlp")(_.simple.updatable)
      .property[Int]("pap")(_.simple.updatable)
      .property[String]("status")(_.simple.updatable)
      .property[Option[String]]("summary")(_.simple.updatable)
      .property[Option[String]]("owner")(_.derived(_.outTo[CaseUser].value[String]("login")).custom {
        (_, _, login: Option[String], vertex, _, graph, authContext) ⇒
          for {
            case0 ← caseSrv.get(vertex)(graph).getOrFail()
            user  ← login.map(userSrv.get(_)(graph).getOrFail()).flip
            _ ← user match {
              case Some(u) ⇒ caseSrv.assign(case0, u)(graph, authContext)
              case None    ⇒ caseSrv.unassign(case0)(graph, authContext)
            }
          } yield Json.obj("owner" → user.map(_.login))
      })
      .property[String]("resolutionStatus")(_.derived(_.outTo[CaseResolutionStatus].value[String]("name")).readonly)
      .property[String]("customFieldName")(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property[String]("customFieldDescription")(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property[String]("customFieldType")(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property[String]("customFieldValue")(
        _.derived(
          _.outToE[CaseCustomField].value[Any]("stringValue"),
          _.outToE[CaseCustomField].value[Any]("booleanValue"),
          _.outToE[CaseCustomField].value[Any]("integerValue"),
          _.outToE[CaseCustomField].value[Any]("floatValue"),
          _.outToE[CaseCustomField].value[Any]("dateValue")
        ).readonly
      )
      .build

  def fromInputCase(inputCase: InputCase, caseTemplate: Option[RichCaseTemplate]): Case =
    caseTemplate.fold(fromInputCase(inputCase)) { ct ⇒
      inputCase
        .into[Case]
        .withFieldComputed(_.title, ct.titlePrefix.getOrElse("") + _.title)
        .withFieldComputed(_.severity, _.severity.orElse(ct.severity).getOrElse(2))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(ct.flag))
        .withFieldComputed(_.tlp, _.tlp.orElse(ct.tlp).getOrElse(2))
        .withFieldComputed(_.pap, _.pap.orElse(ct.pap).getOrElse(2))
        .withFieldComputed(_.tags, _.tags ++ ct.tags)
        .withFieldConst(_.summary, ct.summary)
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .transform
    }
}
