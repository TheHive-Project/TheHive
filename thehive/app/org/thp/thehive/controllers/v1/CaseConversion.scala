package org.thp.thehive.controllers.v1

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v1.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.CaseSteps

trait CaseConversion extends CustomFieldConversion {
  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
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
      .property[String]("user")(_.simple.updatable)
      .property[String]("customFieldName")(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property[String]("customFieldDescription")(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property[String]("customFieldType")(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property[String]("customFieldValue")(_.derived(
        _.outToE[CaseCustomField].value[String]("stringValue"),
        _.outToE[CaseCustomField].value[String]("booleanValue"),
        _.outToE[CaseCustomField].value[String]("integerValue"),
        _.outToE[CaseCustomField].value[String]("floatValue"),
        _.outToE[CaseCustomField].value[String]("dateValue")
      ).readonly)
      .build
}
