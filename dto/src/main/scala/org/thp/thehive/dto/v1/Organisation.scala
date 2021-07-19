package org.thp.thehive.dto.v1

import org.thp.thehive.dto.{Description, String64}
import play.api.libs.json.{Format, Json, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputOrganisation(
    name: String64,
    description: Description,
    taskRule: Option[String64],
    observableRule: Option[String64]
)

object InputOrganisation {
  implicit val writes: Writes[InputOrganisation] = Json.writes[InputOrganisation]
}

case class OrganisationLink(toOrganisation: String, linkType: String, otherLinkType: String)
object OrganisationLink {
  implicit val format: Format[OrganisationLink] = Json.format[OrganisationLink]
}

case class OutputSharingProfile(
    name: String,
    description: String,
    autoShare: Boolean,
    editable: Boolean,
    permissionProfile: String,
    taskRule: String,
    observableRule: String
)
object OutputSharingProfile {
  implicit val format: Format[OutputSharingProfile] = Json.format[OutputSharingProfile]
}

case class OutputOrganisation(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    description: String,
    taskRule: String,
    observableRule: String,
    links: Seq[OrganisationLink]
)

object OutputOrganisation {
  implicit val format: Format[OutputOrganisation] = Json.format[OutputOrganisation]
}
