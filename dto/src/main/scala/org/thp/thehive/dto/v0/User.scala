package org.thp.thehive.dto.v0

import org.thp.scalligraph.controllers.FFile
import org.thp.thehive.dto.{String128, String64}
import play.api.libs.json.{Json, OFormat, Writes}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputUser(
    login: String128,
    name: String128,
    roles: Seq[String64],
    password: Option[String128],
    organisation: Option[String64] = None,
    avatar: Option[FFile]
)

object InputUser {
  implicit val writes: Writes[InputUser] = Json.writes[InputUser]
}

case class OutputUser(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String],
    createdAt: Date,
    updatedAt: Option[Date],
    _type: String,
    login: String,
    name: String,
    roles: Set[String],
    organisation: String,
    hasKey: Boolean,
    status: String
)

object OutputUser {
  implicit val format: OFormat[OutputUser] = Json.format[OutputUser]
}
