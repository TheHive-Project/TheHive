package org.thp.cortex.dto.v0

import play.api.libs.json._

object CortexOperationType extends Enumeration {
  type Type = Value

  val AddTagToCase, AddTagToArtifact, CreateTask, AddCustomFields, CloseTask, MarkAlertAsRead, AddLogToTask, AddArtifactToCase, AssignCase,
      AddTagToAlert, Unknown = Value

  implicit val format: Format[Type] = Json.formatEnum(CortexOperationType)
}

case class CortexOutputOperation(
    `type`: CortexOperationType.Type,
    tag: Option[String],
    title: Option[String],
    description: Option[String],
    name: Option[String],
    tpe: Option[String],
    value: Option[JsObject],
    content: Option[String],
    owner: Option[String],
    data: Option[String],
    dataType: Option[String],
    message: Option[String]
)

object CortexOutputOperation {
  implicit val writes: Writes[CortexOutputOperation] = Json.writes[CortexOutputOperation]
  implicit val reads: Reads[CortexOutputOperation] = Reads[CortexOutputOperation](
    json =>
      for {
        t <- (json \ "type").validate[CortexOperationType.Type].orElse(JsSuccess(CortexOperationType.Unknown))
        tag         = (json \ "tag").asOpt[String]
        title       = (json \ "title").asOpt[String]
        description = (json \ "description").asOpt[String]
        name        = (json \ "name").asOpt[String]
        tpe         = (json \ "tpe").asOpt[String]
        value       = (json \ "value").asOpt[JsObject]
        content     = (json \ "content").asOpt[String]
        owner       = (json \ "owner").asOpt[String]
        data        = (json \ "data").asOpt[String]
        dataType    = (json \ "dataType").asOpt[String]
        message     = (json \ "message").asOpt[String]
      } yield CortexOutputOperation(
        t,
        tag,
        title,
        description,
        name,
        tpe,
        value,
        content,
        owner,
        data,
        dataType,
        message
      )
  )
}
