package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

import org.thp.scalligraph.controllers.WithParser

case class InputCaseTemplate(
    name: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int] = None,
    tags: Seq[String] = Nil,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    summary: Option[String] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil)

object InputCaseTemplate {
  implicit val writes: OWrites[InputCaseTemplate] = Json.writes[InputCaseTemplate]
}

case class OutputCaseTemplate(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    titlePrefix: Option[String],
    description: Option[String],
    severity: Option[Int],
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Option[Int],
    pap: Option[Int],
    summary: Option[String],
    customFields: Set[OutputCustomFieldValue] = Set.empty)

object OutputCaseTemplate {
  implicit val format: OFormat[OutputCaseTemplate] = Json.format[OutputCaseTemplate]
}
