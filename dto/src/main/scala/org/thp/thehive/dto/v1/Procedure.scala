package org.thp.thehive.dto.v1

import play.api.libs.json.{Format, JsObject, Json, Reads, Writes}

import java.util.Date

case class InputProcedure(
    description: String,
    occurDate: Date,
    caseId: String,
    patternId: String
)

object InputProcedure {
  implicit val reads: Reads[InputProcedure] = Reads[InputProcedure] { json =>
    for {
      description <- (json \ "description").validate[String]
      occurDate   <- (json \ "occurDate").validate[Date]
      caseId      <- (json \ "caseId").validate[String]
      patternId   <- (json \ "patternId").validate[String]
    } yield InputProcedure(
      description,
      occurDate,
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
    description: String,
    occurDate: Date,
    patternId: String,
    extraData: JsObject
)

object OutputProcedure {
  implicit val format: Format[OutputProcedure] = Json.format[OutputProcedure]
}
