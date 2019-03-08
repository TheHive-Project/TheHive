package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json.{JsValue, Json, OFormat, OWrites}

import org.thp.scalligraph.controllers.WithParser

case class InputCase(
    title: String,
    description: String,
    severity: Option[Int] = None,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    tags: Seq[String] = Nil,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    status: Option[String] = None,
    summary: Option[String] = None,
    user: Option[String] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil)

object InputCase {
  implicit val writes: OWrites[InputCase] = Json.writes[InputCase]
}

case class OutputCase(
    _id: String,
    id: String, // _id
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    caseId: Int, // number
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    owner: Option[String], // user
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    stats: JsValue
)

object OutputCase {
  implicit val format: OFormat[OutputCase] = Json.format[OutputCase]
}
