package org.thp.thehive.connector.cortex.models

import org.thp.thehive.connector.cortex.models.ActionOperationStatus.ActionOperationStatus
import play.api.libs.json._

object ActionOperationStatus extends Enumeration {
  type ActionOperationStatus = Value
  val Waiting, Success, Failure = Value
}

/**
  * Base trait for all Action operations available
  */
trait ActionOperation {
  val status: ActionOperationStatus
  val message: String

  def updateStatus(newStatus: ActionOperationStatus, newMessage: String): ActionOperation
}

case class AddTagToCase(tag: String, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AddTagToCase = copy(status = newStatus, message = newMessage)
}

case class AddTagToArtifact(tag: String, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "")
    extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AddTagToArtifact =
    copy(status = newStatus, message = newMessage)
}

case class CreateTask(title: String, description: String, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "")
    extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): CreateTask = copy(status = newStatus, message = newMessage)
}

case class AddCustomFields(
    name: String,
    tpe: String,
    value: Any,
    status: ActionOperationStatus = ActionOperationStatus.Waiting,
    message: String = ""
) extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AddCustomFields =
    copy(status = newStatus, message = newMessage)
}

case class CloseTask(status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): CloseTask = copy(status = newStatus, message = newMessage)
}

case class MarkAlertAsRead(status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): MarkAlertAsRead =
    copy(status = newStatus, message = newMessage)
}

case class AddLogToTask(
    content: String,
    owner: Option[String],
    status: ActionOperationStatus = ActionOperationStatus.Waiting,
    message: String = ""
) extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): ActionOperation = copy(status = newStatus, message = newMessage)
}

case class AddTagToAlert(tag: String, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AddTagToAlert = copy(status = newStatus, message = newMessage)
}

case class AddArtifactToCase(
    data: String,
    dataType: String,
    dataMessage: String,
    status: ActionOperationStatus = ActionOperationStatus.Waiting,
    message: String = ""
) extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AddArtifactToCase =
    copy(status = newStatus, message = newMessage)
}

case class AssignCase(owner: String, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): AssignCase = copy(status = newStatus, message = newMessage)
}

object ActionOperation {
  val addTagToCaseWrites: OWrites[AddTagToCase]         = Json.writes[AddTagToCase]
  val addTagToArtifactWrites: OWrites[AddTagToArtifact] = Json.writes[AddTagToArtifact]
  val createTaskWrites: OWrites[CreateTask]             = Json.writes[CreateTask]

  val addCustomFieldsWrites: OWrites[AddCustomFields] = (o: AddCustomFields) =>
    Json.obj(
      "name"    -> o.name,
      "tpe"     -> o.tpe,
      "value"   -> o.value.toString,
      "message" -> o.message,
      "status"  -> o.status.toString
    )
  val closeTaskWrites: OWrites[CloseTask]                 = Json.writes[CloseTask]
  val markAlertAsReadWrites: OWrites[MarkAlertAsRead]     = Json.writes[MarkAlertAsRead]
  val addLogToTaskWrites: OWrites[AddLogToTask]           = Json.writes[AddLogToTask]
  val addTagToAlertWrites: OWrites[AddTagToAlert]         = Json.writes[AddTagToAlert]
  val addArtifactToCaseWrites: OWrites[AddArtifactToCase] = Json.writes[AddArtifactToCase]
  val assignCaseWrites: OWrites[AssignCase]               = Json.writes[AssignCase]

  implicit val actionOperationWrites: OWrites[ActionOperation] = OWrites[ActionOperation] { operation =>
    (operation match {
      case a: AddTagToCase      => addTagToCaseWrites.writes(a)
      case a: AddTagToArtifact  => addTagToArtifactWrites.writes(a)
      case a: CreateTask        => createTaskWrites.writes(a)
      case a: AddCustomFields   => addCustomFieldsWrites.writes(a)
      case a: CloseTask         => closeTaskWrites.writes(a)
      case a: MarkAlertAsRead   => markAlertAsReadWrites.writes(a)
      case a: AddLogToTask      => addLogToTaskWrites.writes(a)
      case a: AddTagToAlert     => addTagToAlertWrites.writes(a)
      case a: AddArtifactToCase => addArtifactToCaseWrites.writes(a)
      case a: AssignCase        => assignCaseWrites.writes(a)
      case a                    => Json.obj("unsupported operation" -> a.toString)
    }) + ("type" -> JsString(operation.getClass.getSimpleName))
  }
}
