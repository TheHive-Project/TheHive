package org.thp.thehive.dto.v1

import play.api.libs.json._

import java.util.Date

case class InputProcedure(
    description: Option[String],
    occurDate: Date,
    tactic: String,
    caseId: String,
    patternId: String
)

object InputProcedure {
  implicit val reads: Reads[InputProcedure] = Reads[InputProcedure] { json =>
    for {
      description <- (json \ "description").validateOpt[String]
      occurDate   <- (json \ "occurDate").validate[Date]
      tactic      <- (json \ "tactic").validate[String]
      caseId      <- (json \ "caseId").validate[String]
      patternId   <- (json \ "patternId").validate[String]
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
