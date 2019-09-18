package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v1.{InputCaseTemplate, OutputCaseTemplate}
import org.thp.thehive.models.{CaseCustomField, CaseTemplate, CaseTemplateTag, RichCaseTemplate}
import org.thp.thehive.services.{CaseTemplateSrv, CaseTemplateSteps}
import play.api.libs.json.Json
import scala.language.implicitConversions

import org.thp.scalligraph.controllers.Output

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
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .transform
    )

  def caseTemplateProperties(caseTemplateSrv: CaseTemplateSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property("name", UniMapping.string)(_.simple.updatable)
      .property("titlePrefix", UniMapping.string.optional)(_.simple.updatable)
      .property("description", UniMapping.string.optional)(_.simple.updatable)
      .property("severity", UniMapping.int.optional)(_.simple.updatable)
      .property("tags", UniMapping.string.set)(
        _.derived(_.outTo[CaseTemplateTag].value("name"))
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(caseTemplate => caseTemplateSrv.updateTags(caseTemplate, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.simple.updatable)
      .property("tlp", UniMapping.int.optional)(_.simple.updatable)
      .property("pap", UniMapping.int.optional)(_.simple.updatable)
      .property("summary", UniMapping.string.optional)(_.simple.updatable)
      .property("user", UniMapping.string)(_.simple.updatable)
      .property("customFieldName", UniMapping.string)(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property("customFieldDescription", UniMapping.string)(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property("customFieldType", UniMapping.string)(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property("customFieldValue", UniMapping.string)(
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
