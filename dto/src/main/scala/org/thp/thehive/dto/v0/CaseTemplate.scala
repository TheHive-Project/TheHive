package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{JsObject, Json, OFormat, OWrites}

import org.thp.scalligraph.controllers.WithParser

case class InputCaseTemplate(
    name: String,
    comment: Option[String],
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int] = None,
    tags: Seq[String] = Nil,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    summary: Option[String] = None,
    tasks: Seq[InputTask] = Nil,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil
)

object InputCaseTemplate {
  implicit val writes: OWrites[InputCaseTemplate] = Json.writes[InputCaseTemplate]
}

case class OutputCaseTemplate(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    name: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String],
    tasks: Seq[OutputTask],
    status: String,
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    metrics: JsObject = JsObject.empty
)

object OutputCaseTemplate {
  implicit val format: OFormat[OutputCaseTemplate] = Json.format[OutputCaseTemplate]
}
