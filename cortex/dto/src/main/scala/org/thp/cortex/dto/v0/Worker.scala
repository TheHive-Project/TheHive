package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, OFormat, Reads, Writes}

case class OutputCortexWorker(
    id: String,
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    maxTlp: Long,
    maxPap: Long
)

object OutputCortexWorker {
  implicit val writes: Writes[OutputCortexWorker] = Json.writes[OutputCortexWorker]
  implicit val reads: Reads[OutputCortexWorker] = Reads[OutputCortexWorker](
    json =>
      for {
        id           <- (json \ "id").validate[String]
        name         <- (json \ "name").validate[String]
        version      <- (json \ "version").validate[String]
        description  <- (json \ "description").validate[String]
        dataTypeList <- (json \ "dataTypeList").validate[Seq[String]]
        maxTlp = (json \ "maxTlp").asOpt[Long].getOrElse(3L)
        maxPap = (json \ "maxPap").asOpt[Long].getOrElse(3L)
      } yield OutputCortexWorker(
        id,
        name,
        version,
        description,
        dataTypeList,
        maxTlp,
        maxPap
      )
  )
}

case class InputCortexWorker(id: String, name: String, description: String, dataTypeList: Seq[String])

object InputCortexWorker {
  implicit val format: OFormat[InputCortexWorker] = Json.format[InputCortexWorker]
}
