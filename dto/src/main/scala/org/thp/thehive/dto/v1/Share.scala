package org.thp.thehive.dto.v1

import org.thp.thehive.dto.String64
import play.api.libs.json.{Format, Json, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputShare(
    organisation: String64,
    share: Option[Boolean],
    profile: Option[String64],
    taskRule: Option[String64],
    observableRule: Option[String64]
)

object InputShare {
  implicit val writes: Writes[InputShare] = Json.writes[InputShare]
}

case class OutputShare(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    caseId: String,
    profileName: String,
    organisationName: String,
    owner: Boolean
)

object OutputShare {
  implicit val format: Format[OutputShare] = Json.format[OutputShare]
}
