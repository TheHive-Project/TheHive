package org.thp.thehive.dto.v1

import play.api.libs.json._

import java.util.Date

case class InputProcedure(
    description: String,
    occurence: Date,
    caseId: String,
    patternId: String
)

object InputProcedure {
  implicit val reads: Reads[InputProcedure] = Reads[InputProcedure] { json =>
    for {
      description <- (json \ "description").validate[String]
      occurence   <- (json \ "occurence").validate[Date]
      caseId      <- (json \ "caseId").validate[String]
      patternId   <- (json \ "patternId").validate[String]
    } yield InputProcedure(
      description,
      occurence,
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
    occurence: Date,
    patternId: String,
    extraData: JsObject
)

object OutputProcedure {
  implicit val format: Format[OutputProcedure] = Json.format[OutputProcedure]
}
