package org.thp.thehive.dto.v1

import java.util.Date

import org.thp.scalligraph.controllers.WithParser
import play.api.libs.json.{Json, OFormat, OWrites}

case class InputCase(
    title: String,
    description: String,
    severity: Option[Int] = None,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    status: Option[String] = None,
    summary: Option[String] = None,
    user: Option[String] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil
)

object InputCase {
  implicit val writes: OWrites[InputCase] = Json.writes[InputCase]
}

case class OutputCase(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    number: Int,
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
    assignee: Option[String],
    customFields: Set[OutputCustomFieldValue] = Set.empty
)

object OutputCase {
  implicit val format: OFormat[OutputCase] = Json.format[OutputCase]
}
