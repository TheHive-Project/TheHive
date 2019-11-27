package org.thp.thehive.connector.cortex.models

import play.api.libs.json._

/**
  * Base trait for all Action operations available
  */
trait ActionOperation

case class AddTagToCase(tag: String)                                          extends ActionOperation
case class AddTagToArtifact(tag: String)                                      extends ActionOperation
case class CreateTask(title: String, description: String)                     extends ActionOperation
case class AddCustomFields(name: String, tpe: String, value: JsValue)         extends ActionOperation
case class CloseTask()                                                        extends ActionOperation
case class MarkAlertAsRead()                                                  extends ActionOperation
case class AddLogToTask(content: String, owner: Option[String])               extends ActionOperation
case class AddTagToAlert(tag: String)                                         extends ActionOperation
case class AddArtifactToCase(data: String, dataType: String, message: String) extends ActionOperation
case class AssignCase(owner: String)                                          extends ActionOperation

object ActionOperation {
  val addTagToCaseFormat: OFormat[AddTagToCase]           = Json.format[AddTagToCase]
  val addTagToArtifactFormat: OFormat[AddTagToArtifact]   = Json.format[AddTagToArtifact]
  val createTaskFormat: OFormat[CreateTask]               = Json.format[CreateTask]
  val addCustomFieldsFormat: OFormat[AddCustomFields]     = Json.format[AddCustomFields]
  val addLogToTaskFormat: OFormat[AddLogToTask]           = Json.format[AddLogToTask]
  val addTagToAlertFormat: OFormat[AddTagToAlert]         = Json.format[AddTagToAlert]
  val addArtifactToCaseFormat: OFormat[AddArtifactToCase] = Json.format[AddArtifactToCase]
  val assignCaseFormat: OFormat[AssignCase]               = Json.format[AssignCase]

  implicit val actionOperationWrites: OWrites[ActionOperation] = OWrites[ActionOperation] { operation =>
    (operation match {
      case a: AddTagToCase      => addTagToCaseFormat.writes(a)
      case a: AddTagToArtifact  => addTagToArtifactFormat.writes(a)
      case a: CreateTask        => createTaskFormat.writes(a)
      case a: AddCustomFields   => addCustomFieldsFormat.writes(a)
      case _: CloseTask         => JsObject.empty
      case _: MarkAlertAsRead   => JsObject.empty
      case a: AddLogToTask      => addLogToTaskFormat.writes(a)
      case a: AddTagToAlert     => addTagToAlertFormat.writes(a)
      case a: AddArtifactToCase => addArtifactToCaseFormat.writes(a)
      case a: AssignCase        => assignCaseFormat.writes(a)
      case a                    => Json.obj("unsupported operation" -> a.toString)
    }) + ("type" -> JsString(operation.getClass.getSimpleName))
  }

  implicit val actionOperationReads: Reads[ActionOperation] = Reads[ActionOperation] { json =>
    (json \ "type").validate[String].flatMap {
      case "AddTagToCase"      => json.validate(addTagToCaseFormat)
      case "AddTagToArtifact"  => json.validate(addTagToArtifactFormat)
      case "CreateTask"        => json.validate(createTaskFormat)
      case "AddCustomFields"   => json.validate(addCustomFieldsFormat)
      case "CloseTask"         => JsSuccess(CloseTask())
      case "MarkAlertAsRead"   => JsSuccess(MarkAlertAsRead())
      case "AddLogToTask"      => json.validate(addLogToTaskFormat)
      case "AddTagToAlert"     => json.validate(addTagToAlertFormat)
      case "AddArtifactToCase" => json.validate(addArtifactToCaseFormat)
      case "AssignCase"        => json.validate(assignCaseFormat)
    }
  }
}

case class ActionOperationStatus(operation: ActionOperation, success: Boolean, message: String)

object ActionOperationStatus {
  implicit val writes: OWrites[ActionOperationStatus] = OWrites[ActionOperationStatus] { operationStatus =>
    Json.toJsObject(operationStatus.operation) +
      ("status"  -> JsString(if (operationStatus.success) "Success" else "Failure")) +
      ("message" -> JsString(operationStatus.message))
  }
}
