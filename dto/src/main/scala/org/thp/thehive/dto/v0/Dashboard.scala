package org.thp.thehive.dto.v0

import org.thp.thehive.dto.{Description, String16, String512}
import play.api.libs.json.{Json, OFormat, OWrites}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputDashboard(
    title: String512,
    description: Description,
    status: String16,
    definition: Description
)

object InputDashboard {
  implicit val writes: OWrites[InputDashboard] = Json.writes[InputDashboard]
}

case class OutputDashboard(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    title: String,
    description: String,
    status: String,
    definition: String,
    writable: Boolean
)

object OutputDashboard {
  implicit val format: OFormat[OutputDashboard] = Json.format[OutputDashboard]
}
