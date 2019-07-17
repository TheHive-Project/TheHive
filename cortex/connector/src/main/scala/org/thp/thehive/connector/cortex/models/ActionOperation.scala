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

case class CreateTask(fields: JsObject, status: ActionOperationStatus = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus, newMessage: String): CreateTask = copy(status = newStatus, message = newMessage)
}

case class AddCustomFields(
    name: String,
    tpe: String,
    value: JsValue,
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
  val addTagToCaseWrites: OWrites[AddTagToCase]           = Json.writes[AddTagToCase]
  val addTagToArtifactWrites: OWrites[AddTagToArtifact]   = Json.writes[AddTagToArtifact]
  val createTaskWrites: OWrites[CreateTask]               = Json.writes[CreateTask]
  val addCustomFieldsWrites: OWrites[AddCustomFields]     = Json.writes[AddCustomFields]
  val closeTaskWrites: OWrites[CloseTask]                 = Json.writes[CloseTask]
  val markAlertAsReadWrites: OWrites[MarkAlertAsRead]     = Json.writes[MarkAlertAsRead]
  val addLogToTaskWrites: OWrites[AddLogToTask]           = Json.writes[AddLogToTask]
  val addTagToAlertWrites: OWrites[AddTagToAlert]         = Json.writes[AddTagToAlert]
  val addArtifactToCaseWrites: OWrites[AddArtifactToCase] = Json.writes[AddArtifactToCase]
  val assignCaseWrites: OWrites[AssignCase]               = Json.writes[AssignCase]
  implicit val actionOperationReads: Reads[ActionOperation] = Reads[ActionOperation](
    json =>
      (json \ "type").asOpt[String].fold[JsResult[ActionOperation]](JsError("type is missing in action operation")) {
        case "AddTagToCase"     => (json \ "tag").validate[String].map(tag => AddTagToCase(tag))
        case "AddTagToArtifact" => (json \ "tag").validate[String].map(tag => AddTagToArtifact(tag))
        case "CreateTask"       => JsSuccess(CreateTask(json.as[JsObject] - "type"))
        case "AddCustomFields" =>
          for {
            name  <- (json \ "name").validate[String]
            tpe   <- (json \ "tpe").validate[String]
            value <- (json \ "value").validate[JsValue]
          } yield AddCustomFields(name, tpe, value)
        case "CloseTask"       => JsSuccess(CloseTask())
        case "MarkAlertAsRead" => JsSuccess(MarkAlertAsRead())
        case "AddLogToTask" =>
          for {
            content <- (json \ "content").validate[String]
            owner   <- (json \ "owner").validateOpt[String]
          } yield AddLogToTask(content, owner)
        case "AddArtifactToCase" =>
          for {
            data        <- (json \ "data").validate[String]
            dataType    <- (json \ "dataType").validate[String]
            dataMessage <- (json \ "message").validate[String]
          } yield AddArtifactToCase(data, dataType, dataMessage)
        case "AssignCase" =>
          for {
            owner <- (json \ "owner").validate[String]
          } yield AssignCase(owner)
        case "AddTagToAlert" => (json \ "tag").validate[String].map(tag => AddTagToAlert(tag))
        case other           => JsError(s"Unknown operation $other")
      }
  )
  implicit val actionOperationWrites: Writes[ActionOperation] = Writes[ActionOperation] {
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
  }
}
