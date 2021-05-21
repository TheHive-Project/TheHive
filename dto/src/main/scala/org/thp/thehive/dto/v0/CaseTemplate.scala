package org.thp.thehive.dto.v0

import org.thp.scalligraph.controllers.WithParser
import org.thp.thehive.dto.{Description, Pap, Severity, String128, String64, Tlp}
import play.api.libs.json.{JsObject, Json, OFormat, OWrites}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputCaseTemplate(
    name: String64,
    displayName: Option[String64],
    titlePrefix: Option[String64],
    description: Option[Description],
    severity: Option[Severity] = None,
    tags: Set[String128] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Tlp] = None,
    pap: Option[Pap] = None,
    summary: Option[Description] = None,
    tasks: Seq[InputTask] = Nil,
    @WithParser(InputCustomFieldValue.parser)
    customFields: Seq[InputCustomFieldValue] = Nil
)

object InputCaseTemplate {
  implicit val writes: OWrites[InputCaseTemplate] = Json.writes[InputCaseTemplate]
}

case class OutputCaseTemplate(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String],
    createdAt: Date,
    updatedAt: Option[Date],
    _type: String,
    name: String,
    displayName: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    tags: Set[String],
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String],
    tasks: Seq[OutputTask],
    status: String,
    customFields: JsObject,
    metrics: JsObject
)

object OutputCaseTemplate {
  implicit val format: OFormat[OutputCaseTemplate] = Json.format[OutputCaseTemplate]
}
