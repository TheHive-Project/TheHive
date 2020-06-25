package org.thp.thehive.dto.v0

import java.util.Date

import org.thp.scalligraph.controllers.WithParser
import play.api.libs.json.{JsObject, Json, OFormat, OWrites}

case class InputCaseTemplate(
    name: String,
    displayName: Option[String],
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int] = None,
    tags: Set[String] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    summary: Option[String] = None,
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
