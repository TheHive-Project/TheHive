package org.thp.thehive.dto.v1

import org.thp.scalligraph.controllers.WithParser
import org.thp.thehive.dto.{Description, Pap, Severity, String128, String64, Tlp}
import play.api.libs.json.{Json, OFormat, OWrites}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputCaseTemplate(
    name: String128,
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
    customFieldValue: Seq[InputCustomFieldValue] = Nil
)

object InputCaseTemplate {
  implicit val writes: OWrites[InputCaseTemplate] = Json.writes[InputCaseTemplate]
}

case class OutputCaseTemplate(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    displayName: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String],
    customFields: Seq[OutputCustomFieldValue] = Seq.empty,
    tasks: Seq[OutputTask]
)

object OutputCaseTemplate {
  implicit val format: OFormat[OutputCaseTemplate] = Json.format[OutputCaseTemplate]
}
