package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
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
        .withFieldComputed(_.task, _.tasks.map(toOutputTask(_).toOutput))
        .transform
    )

  val caseTemplateProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property[String]("name")(_.simple.updatable)
      .property[Option[String]]("titlePrefix")(_.simple.updatable)
      .property[Option[String]]("description")(_.simple.updatable)
      .property[Option[Int]]("severity")(_.simple.updatable)
      .property[Set[String]]("tags")(_.simple.updatable)
      .property[Boolean]("flag")(_.simple.updatable)
      .property[Option[Int]]("tlp")(_.simple.updatable)
      .property[Option[Int]]("pap")(_.simple.updatable)
      .property[Option[String]]("summary")(_.simple.updatable)
      .property[String]("user")(_.simple.updatable)
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
}
