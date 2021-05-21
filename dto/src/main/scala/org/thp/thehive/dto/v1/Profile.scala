package org.thp.thehive.dto.v1

import org.thp.thehive.dto.{String32, String64}
import play.api.libs.json.{Json, OFormat, OWrites}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputProfile(name: String64, permissions: Set[String32])

object InputProfile {
  implicit val writes: OWrites[InputProfile] = Json.writes[InputProfile]
}

case class OutputProfile(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    permissions: Seq[String],
    editable: Boolean,
    isAdmin: Boolean
)

object OutputProfile {
  implicit val format: OFormat[OutputProfile] = Json.format[OutputProfile]
}
