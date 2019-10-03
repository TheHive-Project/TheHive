package org.thp.thehive.controllers.v1

import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseSteps, UserSrv}

object CaseConversion {
  import CustomFieldConversion._

  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )
  implicit def toOutputCaseWithStats(richCaseWithStats: (RichCase, JsObject)): Output[OutputCase] =
    toOutputCase(richCaseWithStats._1) // TODO add stats

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

  def caseProperties(caseSrv: CaseSrv, userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseSteps]
      .property("title", UniMapping.string)(_.simple.updatable)
      .property("description", UniMapping.string)(_.simple.updatable)
      .property("severity", UniMapping.int)(_.simple.updatable)
      .property("startDate", UniMapping.date)(_.simple.updatable)
      .property("endDate", UniMapping.date.optional)(_.simple.updatable)
      .property("tags", UniMapping.string.set)(
        _.derived(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(`case` => caseSrv.updateTagNames(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("tlp", UniMapping.int)(_.simple.updatable)
      .property("pap", UniMapping.int)(_.simple.updatable)
      .property("status", UniMapping.string)(_.simple.updatable)
      .property("summary", UniMapping.string.optional)(_.simple.updatable)
      .property("user", UniMapping.string.optional)(_.derived(_.user.login).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail()
          user <- login.map(userSrv.get(_)(graph).getOrFail()).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("customFieldName", UniMapping.string)(_.derived(_.customFields.name).readonly)
      .property("customFieldDescription", UniMapping.string)(_.derived(_.customFields.description).readonly)
//      .property("customFieldType", UniMapping.string)(_.derived(_.customFields.`type`).readonly)
//      .property("customFieldValue", UniMapping.string)(_.derived(_.customFieldsValue.map(_.value.toString)).readonly)
      .build
}
