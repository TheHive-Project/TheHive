package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputCaseTemplate, OutputCaseTemplate}
import org.thp.thehive.models.{CaseCustomField, CaseTemplate, RichCaseTemplate}
import org.thp.thehive.services.CaseTemplateSteps

object CaseTemplateConversion {
  import TaskConversion._
  import CustomFieldConversion._

  implicit def fromInputCaseTemplate(inputCaseTemplate: InputCaseTemplate): CaseTemplate =
    inputCaseTemplate
      .into[CaseTemplate]
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .transform

  implicit def toOutputCaseTemplate(richCaseTemplate: RichCaseTemplate): Output[OutputCaseTemplate] =
    Output[OutputCaseTemplate](
      richCaseTemplate
        .into[OutputCaseTemplate]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldRenamed(_._id, _.id)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldConst(_.status, "Ok")
        .withFieldConst(_._type, "caseTemplate")
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldComputed(_.task, _.tasks.map(toOutputTask(_).toOutput))
        .transform
    )

  val caseTemplateProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property("name", UniMapping.stringMapping)(_.simple.updatable)
      .property("titlePrefix", UniMapping.stringMapping.optional)(_.simple.updatable)
      .property("description", UniMapping.stringMapping.optional)(_.simple.updatable)
      .property("severity", UniMapping.intMapping.optional)(_.simple.updatable)
      .property("tags", UniMapping.stringMapping.optional)(_.simple.updatable) // FIXME
      .property("flag", UniMapping.booleanMapping)(_.simple.updatable)
      .property("tlp", UniMapping.intMapping.optional)(_.simple.updatable)
      .property("pap", UniMapping.intMapping.optional)(_.simple.updatable)
      .property("summary", UniMapping.stringMapping.optional)(_.simple.updatable)
      .property("user", UniMapping.stringMapping)(_.simple.updatable)
      .property("customFieldName", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property("customFieldDescription", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property("customFieldType", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property("customFieldValue", UniMapping.stringMapping)(
        _.derived(
          _.outToE[CaseCustomField].value("stringValue"),
          _.outToE[CaseCustomField].value("booleanValue"),
          _.outToE[CaseCustomField].value("integerValue"),
          _.outToE[CaseCustomField].value("floatValue"),
          _.outToE[CaseCustomField].value("dateValue")
        ).readonly
      )
      .build
}
