package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class OutputWorker(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Long,
    maxPap: Long
)

object OutputWorker {
  implicit val writes: Writes[OutputWorker] = Json.writes[OutputWorker]
  implicit val reads: Reads[OutputWorker] = Reads[OutputWorker](json =>
    for {
      id           <- (json \ "id").validate[String]
      name         <- (json \ "name").validate[String]
      version      <- (json \ "version").validate[String]
      description  <- (json \ "description").validate[String]
      dataTypeList <- (json \ "dataTypeList").validateOpt[Seq[String]]
      maxTlp = (json \ "maxTlp").asOpt[Long].getOrElse(3L)
      maxPap = (json \ "maxPap").asOpt[Long].getOrElse(3L)
    } yield OutputWorker(
      id,
      name,
      version,
      description,
      dataTypeList.getOrElse(Nil),
      maxTlp,
      maxPap
    )
  )
}

case class InputWorker(id: String, name: String, description: String, dataTypeList: Seq[String])

object InputWorker {
  implicit val format: OFormat[InputWorker] = Json.format[InputWorker]
}
