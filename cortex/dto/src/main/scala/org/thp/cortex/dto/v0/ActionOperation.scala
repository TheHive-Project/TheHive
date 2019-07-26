package org.thp.cortex.dto.v0

import play.api.libs.json.{Format, JsObject, Json, OFormat}

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
  implicit val format: OFormat[CortexOutputOperation] = Json.format[CortexOutputOperation]
}
