package org.thp.thehive.controllers.v0

import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.JsObject

import gremlin.scala.Vertex
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services._
import org.thp.scalligraph.query.PublicProperty.readonly
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseSteps

trait CaseConversion extends CustomFieldConversion {
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
        .transform)

  implicit def fromInputCase(inputCase: InputCase): Case =
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
        .withFieldConst(_.status, CaseStatus.open)
        .withFieldConst(_.number, 0)
        .transform
    }

  def caseProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[CaseSteps, Vertex]
      .property[String]("title").simple
      .property[String]("description").simple
      .property[Int]("severity").simple
      .property[Date]("startDate").simple
      .property[Option[Date]]("endDate") .simple
      .property[Set[String]]("tags").simple
      .property[Boolean]("flag").simple
      .property[Int]("tlp").simple
      .property[Int]("pap").simple
      .property[String]("status").simple
      .property[Option[String]]("summary").simple
      .property[String]("user").simple
      .property[String]("resolutionStatus").derived(_.outTo[CaseResolutionStatus].value("name"))(readonly)
      .property[String]("customFieldName").derived(_.outTo[CaseCustomField].value("name"))(readonly)
      .property[String]("customFieldDescription").derived(_.outTo[CaseCustomField].value("description"))(readonly)
      .property[String]("customFieldType").derived(_.outTo[CaseCustomField].value("type"))(readonly)
      .property[String]("customFieldValue").derived(
      _.outToE[CaseCustomField].value("stringValue"),
      _.outToE[CaseCustomField].value("booleanValue"),
      _.outToE[CaseCustomField].value("integerValue"),
      _.outToE[CaseCustomField].value("floatValue"),
      _.outToE[CaseCustomField].value("dateValue"))(readonly)
      .build
  // format: on

}
