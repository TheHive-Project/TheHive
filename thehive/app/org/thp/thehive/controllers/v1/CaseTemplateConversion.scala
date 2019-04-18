package org.thp.thehive.controllers.v1

import scala.language.implicitConversions

import gremlin.scala.Vertex
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PublicProperty.readonly
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services.RichGremlinScala
import org.thp.thehive.dto.v1.{InputCaseTemplate, OutputCaseTemplate}
import org.thp.thehive.models.{CaseCustomField, CaseTemplate, RichCaseTemplate}
import org.thp.thehive.services.CaseTemplateSteps

trait CaseTemplateConversion extends CustomFieldConversion {
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
        .transform)

  def outputCaseTemplateProperties(implicit db: Database): List[PublicProperty[Vertex, _, _]] =
    // format: off
    PublicPropertyListBuilder[CaseTemplateSteps, Vertex]
      .property[String]("name").simple
      .property[Option[String]]("titlePrefix").simple
      .property[Option[String]]("description").simple
      .property[Option[Int]]("severity").simple
      .property[Set[String]]("tags").simple
      .property[Boolean]("flag").simple
      .property[Option[Int]]("tlp").simple
      .property[Option[Int]]("pap").simple
      .property[Option[String]]("summary").simple
      .property[String]("user").simple
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
