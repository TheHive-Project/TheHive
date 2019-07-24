package org.thp.thehive.controllers.v1

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v1.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseSteps}
import play.api.libs.json.Json

import scala.language.implicitConversions

object CaseConversion {
  import CustomFieldConversion._

  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldComputed(_.status, _.status.toString)
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

  def fromInputCase(inputCase: InputCase, caseTemplate: Option[RichCaseTemplate]): Case =
    caseTemplate.fold(fromInputCase(inputCase)) { ct =>
      inputCase
        .into[Case]
        .withFieldComputed(_.title, ct.titlePrefix.getOrElse("") + _.title)
        .withFieldComputed(_.severity, _.severity.orElse(ct.severity).getOrElse(2))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(ct.flag))
        .withFieldComputed(_.tlp, _.tlp.orElse(ct.tlp).getOrElse(2))
        .withFieldComputed(_.pap, _.pap.orElse(ct.pap).getOrElse(2))
        .withFieldConst(_.summary, ct.summary)
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .transform
    }

  def caseProperties(caseSrv: CaseSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseSteps]
      .property("title", UniMapping.stringMapping)(_.simple.updatable)
      .property("description", UniMapping.stringMapping)(_.simple.updatable)
      .property("severity", UniMapping.intMapping)(_.simple.updatable)
      .property("startDate", UniMapping.dateMapping)(_.simple.updatable)
      .property("endDate", UniMapping.dateMapping.optional)(_.simple.updatable)
      .property("tags", UniMapping.stringMapping.set)(
        _.derived(_.outTo[CaseTag].value[String]("name"))
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(`case` => caseSrv.updateTags(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.booleanMapping)(_.simple.updatable)
      .property("tlp", UniMapping.intMapping)(_.simple.updatable)
      .property("pap", UniMapping.intMapping)(_.simple.updatable)
      .property("status", UniMapping.stringMapping)(_.simple.updatable)
      .property("summary", UniMapping.stringMapping.optional)(_.simple.updatable)
      .property("user", UniMapping.stringMapping)(_.simple.updatable)
      .property("customFieldName", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property("customFieldDescription", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property("customFieldType", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property("customFieldValue", UniMapping.stringMapping)(
        _.derived(
          _.outToE[CaseCustomField].value[String]("stringValue"),
          _.outToE[CaseCustomField].value[String]("booleanValue"),
          _.outToE[CaseCustomField].value[String]("integerValue"),
          _.outToE[CaseCustomField].value[String]("floatValue"),
          _.outToE[CaseCustomField].value[String]("dateValue")
        ).readonly
      )
      .build
}
