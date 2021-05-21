package org.thp.thehive.dto.v1

import org.thp.thehive.dto.{Description, String128, String32}
import play.api.libs.json._

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputProcedure(
    description: Option[Description],
    occurDate: Date,
    tactic: String32,
    caseId: String128,
    patternId: String128
)

object InputProcedure {
  implicit val reads: Reads[InputProcedure] = Reads[InputProcedure] { json =>
    for {
      description <- (json \ "description").validateOpt[Description]
      occurDate   <- (json \ "occurDate").validate[Date]
      tactic      <- (json \ "tactic").validate[String32]
      caseId      <- (json \ "caseId").validate[String128]
      patternId   <- (json \ "patternId").validate[String128]
    } yield InputProcedure(
      description,
      occurDate,
      tactic,
      caseId,
      patternId
    )
  }

  implicit val writes: Writes[InputProcedure] = Json.writes[InputProcedure]
}

case class OutputProcedure(
    _id: String,
    _createdAt: Date,
    _createdBy: String,
    _updatedAt: Option[Date],
    _updatedBy: Option[String],
    description: Option[String],
    occurDate: Date,
    patternId: String,
    tactic: String,
    extraData: JsObject
)

object OutputProcedure {
  implicit val format: Format[OutputProcedure] = Json.format[OutputProcedure]
}
