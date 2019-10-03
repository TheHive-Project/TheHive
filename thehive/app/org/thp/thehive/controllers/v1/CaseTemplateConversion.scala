package org.thp.thehive.controllers.v1

import scala.language.implicitConversions

import play.api.libs.json.Json

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputCaseTemplate, OutputCaseTemplate}
import org.thp.thehive.models.{CaseTemplate, RichCaseTemplate}
import org.thp.thehive.services.{CaseTemplateSrv, CaseTemplateSteps}

object CaseTemplateConversion {

  import CustomFieldConversion._

  implicit def fromInputCaseTemplate(inputCaseTemplate: InputCaseTemplate): CaseTemplate =
    inputCaseTemplate
      .into[CaseTemplate]
      .withFieldComputed(_.displayName, _.displayName.getOrElse(""))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .transform

  implicit def toOutputCaseTemplate(richCaseTemplate: RichCaseTemplate): Output[OutputCaseTemplate] =
    Output[OutputCaseTemplate](
      richCaseTemplate
        .into[OutputCaseTemplate]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .transform
    )

  def caseTemplateProperties(caseTemplateSrv: CaseTemplateSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property("name", UniMapping.string)(_.simple.updatable)
      .property("titlePrefix", UniMapping.string.optional)(_.simple.updatable)
      .property("description", UniMapping.string.optional)(_.simple.updatable)
      .property("severity", UniMapping.int.optional)(_.simple.updatable)
      .property("tags", UniMapping.string.set)(
        _.derived(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(caseTemplate => caseTemplateSrv.updateTagNames(caseTemplate, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("tlp", UniMapping.int.optional)(_.simple.updatable)
      .property("pap", UniMapping.int.optional)(_.simple.updatable)
      .property("summary", UniMapping.string.optional)(_.simple.updatable)
      .property("user", UniMapping.string)(_.simple.updatable)
      .property("customFieldName", UniMapping.string)(_.derived(_.customFields.name).readonly)
      .property("customFieldDescription", UniMapping.string)(_.derived(_.customFields.description).readonly)
//      .property("customFieldType", UniMapping.string)(_.derived(_.customFields.`type`).readonly)
//      .property("customFieldValue", UniMapping.string)(_.derived(_.customFieldsValue.map(_.value.toString)).readonly)
      .build
}
