package org.thp.thehive.dto.v1

import org.thp.scalligraph.controllers.FFile
import org.thp.thehive.dto.{String128, String64}
import play.api.libs.json.{Json, OFormat, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputUser(
    login: String128,
    name: String128,
    password: Option[String128],
    profile: String64,
    organisation: Option[String128],
    avatar: Option[FFile]
)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputOrganisationProfile(organisationId: String, organisation: String, profile: String)
object OutputOrganisationProfile {
  implicit val format: OFormat[OutputOrganisationProfile] = Json.format[OutputOrganisationProfile]
}

case class OutputUser(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    login: String,
    name: String,
    hasKey: Boolean,
    hasPassword: Boolean,
    hasMFA: Boolean,
    locked: Boolean,
    profile: String,
    permissions: Set[String],
    organisation: String,
    avatar: Option[String],
    organisations: Seq[OutputOrganisationProfile]
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
