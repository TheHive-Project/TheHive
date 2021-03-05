package org.thp.thehive.dto.v1

import play.api.libs.json.{Json, OFormat, OWrites}

import java.util.Date

case class InputDashboard(title: String, description: String, status: String, definition: String)

object InputDashboard {
  implicit val writes: OWrites[InputDashboard] = Json.writes[InputDashboard]
}

case class OutputDashboard(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    title: String,
    description: String,
    status: String,
    definition: String
)

object OutputDashboard {
  implicit val format: OFormat[OutputDashboard] = Json.format[OutputDashboard]
}
