package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json._

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import connectors.cortex.services.ActionOperationStatus.Type
import javax.inject.{ Inject, Provider, Singleton }
import models._
import org.elasticsearch.index.engine.VersionConflictEngineException
import services._

import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.models.{ BaseEntity, ChildModelDef, HiveEnumeration }
import org.elastic4play.services.{ AuthContext, FindSrv }
import org.elastic4play.utils.Retry
import org.elastic4play.{ BadRequestError, InternalError }

object ActionOperationStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Waiting, Success, Failure = Value
}

trait ActionOperation {
  val status: ActionOperationStatus.Type
  val message: String

  def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): ActionOperation
}

case class AddTagToCase(tag: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddTagToCase = copy(status = newStatus, message = newMessage)
}

case class AddTagToArtifact(tag: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddTagToArtifact = copy(status = newStatus, message = newMessage)
}

case class CreateTask(fields: JsObject, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): CreateTask = copy(status = newStatus, message = newMessage)
}

case class AddCustomFields(name: String, tpe: String, value: JsValue, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddCustomFields = copy(status = newStatus, message = newMessage)
}

case class CloseTask(status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): CloseTask = copy(status = newStatus, message = newMessage)
}

case class MarkAlertAsRead(status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): MarkAlertAsRead = copy(status = newStatus, message = newMessage)
}

case class AddLogToTask(content: String, owner: Option[String], status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: Type, newMessage: String): ActionOperation = copy(status = newStatus, message = newMessage)
}

case class AddTagToAlert(tag: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddTagToAlert = copy(status = newStatus, message = newMessage)
}

case class AddArtifactToCase(data: String, dataType: String, dataMessage: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddArtifactToCase = copy(status = newStatus, message = newMessage)
}

case class AssignCase(owner: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  override def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AssignCase = copy(status = newStatus, message = newMessage)
}

object ActionOperation {
  val addTagToCaseWrites = Json.writes[AddTagToCase]
  val addTagToArtifactWrites = Json.writes[AddTagToArtifact]
  val createTaskWrites = Json.writes[CreateTask]
  val addCustomFieldsWrites = Json.writes[AddCustomFields]
  val closeTaskWrites = Json.writes[CloseTask]
  val markAlertAsReadWrites = Json.writes[MarkAlertAsRead]
  val addLogToTaskWrites = Json.writes[AddLogToTask]
  val addTagToAlertWrites = Json.writes[AddTagToAlert]
  val addArtifactToCaseWrites = Json.writes[AddArtifactToCase]
  val assignCaseWrites = Json.writes[AssignCase]
  implicit val actionOperationReads: Reads[ActionOperation] = Reads[ActionOperation](json ⇒
    (json \ "type").asOpt[String].fold[JsResult[ActionOperation]](JsError("type is missing in action operation")) {
      case "AddTagToCase"     ⇒ (json \ "tag").validate[String].map(tag ⇒ AddTagToCase(tag))
      case "AddTagToArtifact" ⇒ (json \ "tag").validate[String].map(tag ⇒ AddTagToArtifact(tag))
      case "CreateTask"       ⇒ JsSuccess(CreateTask(json.as[JsObject] - "type"))
      case "AddCustomFields" ⇒ for {
        name ← (json \ "name").validate[String]
        tpe ← (json \ "tpe").validate[String]
        value ← (json \ "value").validate[JsValue]
      } yield AddCustomFields(name, tpe, value)
      case "CloseTask"       ⇒ JsSuccess(CloseTask())
      case "MarkAlertAsRead" ⇒ JsSuccess(MarkAlertAsRead())
      case "AddLogToTask" ⇒ for {
        content ← (json \ "content").validate[String]
        owner ← (json \ "owner").validateOpt[String]
      } yield AddLogToTask(content, owner)
      case "AddTagToAlert" => (json \ "tag").validate[String].map(tag ⇒ AddTagToAlert(tag))
      case "AddArtifactToCase" ⇒ for {
        data ← (json \ "data").validate[String]
        dataType ← (json \ "dataType").validate[String]
        dataMessage ← (json \ "message").validate[String]
      } yield AddArtifactToCase(data, dataType, dataMessage)
      case "AssignCase" for {
        owner  ← (json \ "owner").validate[String]
      } yield AssignCase(owner)
      case other ⇒ JsError(s"Unknown operation $other")
    })
  implicit val actionOperationWrites: Writes[ActionOperation] = Writes[ActionOperation] {
    case a: AddTagToCase      ⇒ addTagToCaseWrites.writes(a)
    case a: AddTagToArtifact  ⇒ addTagToArtifactWrites.writes(a)
    case a: CreateTask        ⇒ createTaskWrites.writes(a)
    case a: AddCustomFields   ⇒ addCustomFieldsWrites.writes(a)
    case a: CloseTask         ⇒ closeTaskWrites.writes(a)
    case a: MarkAlertAsRead   ⇒ markAlertAsReadWrites.writes(a)
    case a: AddLogToTask      ⇒ addLogToTaskWrites.writes(a)
    case a: AddTagToAlert     ⇒ addTagToAlertWrites.writes(a)
    case a: AddArtifactToCase ⇒ addArtifactToCaseWrites.writes(a)
    case a: AssignCase        ⇒ assignCaseWrites.writes(a)
    case a                    ⇒ Json.obj("unsupported operation" → a.toString)
  }
}

@Singleton
class ActionOperationSrv @Inject() (
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    logSrv: LogSrv,
    alertSrvProvider: Provider[AlertSrv],
    findSrv: FindSrv,
    artifactSrv: ArtifactSrv,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  lazy val logger = Logger(getClass)
  lazy val alertSrv: AlertSrv = alertSrvProvider.get

  def findCaseEntity(entity: BaseEntity): Future[Case] = {
    import org.elastic4play.services.QueryDSL._

    (entity, entity.model) match {
      case (c: Case, _)  ⇒ Future.successful(c)
      case (a: Alert, _) ⇒ a.caze().fold(Future.failed[Case](BadRequestError("Alert hasn't been imported to case")))(caseSrv.get)
      case (_, model: ChildModelDef[_, _, _, _]) ⇒
        findSrv(model.parentModel, "_id" ~= entity.parentId.getOrElse(throw InternalError(s"Child entity $entity has no parent ID")), Some("0-1"), Nil)
          ._1.runWith(Sink.head).flatMap(findCaseEntity _)
      case _ ⇒ Future.failed(BadRequestError("Case not found"))
    }
  }

  def findTaskEntity(entity: BaseEntity): Future[Task] = {
    import org.elastic4play.services.QueryDSL._

    (entity, entity.model) match {
      case (a: Task, _) ⇒ Future.successful(a)
      case (_, model: ChildModelDef[_, _, _, _]) ⇒
        findSrv(model.parentModel, "_id" ~= entity.parentId.getOrElse(throw InternalError(s"Child entity $entity has no parent ID")), Some("0-1"), Nil)
          ._1.runWith(Sink.head).flatMap(findTaskEntity _)
      case _ ⇒ Future.failed(BadRequestError("Task not found"))
    }
  }

  def findArtifactEntity(entity: BaseEntity): Future[Artifact] = {
    import org.elastic4play.services.QueryDSL._

    (entity, entity.model) match {
      case (a: Artifact, _) ⇒ Future.successful(a)
      case (_, model: ChildModelDef[_, _, _, _]) ⇒
        findSrv(model.parentModel, "_id" ~= entity.parentId.getOrElse(throw InternalError(s"Child entity $entity has no parent ID")), Some("0-1"), Nil)
          ._1.runWith(Sink.head).flatMap(findArtifactEntity _)
      case _ ⇒ Future.failed(BadRequestError("Artifact not found"))
    }
  }

  def execute(entity: BaseEntity, operation: ActionOperation)(implicit authContext: AuthContext): Future[ActionOperation] = {
    if (operation.status == ActionOperationStatus.Waiting) {
      Retry()(classOf[VersionConflictEngineException]) {
        operation match {
          case AddTagToCase(tag, _, _) ⇒
            for {
              initialCase ← findCaseEntity(entity)
              caze ← caseSrv.get(initialCase.id)
              _ ← caseSrv.update(caze, Fields.empty.set("tags", Json.toJson((caze.tags() :+ tag).distinct)), ModifyConfig(retryOnConflict = 0, version = Some(caze.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case AddTagToArtifact(tag, _, _) ⇒
            entity match {
              case initialArtifact: Artifact ⇒
                for {
                  artifact ← artifactSrv.get(initialArtifact.artifactId())
                  _ ← artifactSrv.update(artifact.artifactId(), Fields.empty.set("tags", Json.toJson((artifact.tags() :+ tag).distinct)), ModifyConfig(retryOnConflict = 0, version = Some(artifact.version)))
                } yield operation.updateStatus(ActionOperationStatus.Success, "")
              case _ ⇒ Future.failed(BadRequestError("Artifact not found"))
            }
          case CreateTask(fields, _, _) ⇒
            for {
              caze ← findCaseEntity(entity)
              _ ← taskSrv.create(caze, Fields(fields))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case AddCustomFields(name, tpe, value, _, _) ⇒
            for {
              initialCase ← findCaseEntity(entity)
              caze ← caseSrv.get(initialCase.id)
              customFields = caze.customFields().asOpt[JsObject].getOrElse(JsObject.empty) ++ Json.obj(name -> Json.obj(tpe -> value))
              _ ← caseSrv.update(caze, Fields.empty.set("customFields", customFields), ModifyConfig(retryOnConflict = 0, version = Some(caze.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case CloseTask(_, _) ⇒
            for {
              initialTask ← findTaskEntity(entity)
              task ← taskSrv.get(initialTask.id)
              _ ← taskSrv.update(task, Fields.empty.set("status", TaskStatus.Completed.toString).set("flag", JsFalse), ModifyConfig(retryOnConflict = 0, version = Some(task.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case MarkAlertAsRead(_, _) ⇒
            entity match {
              case alert: Alert ⇒ alertSrv.markAsRead(alert).map(_ ⇒ operation.updateStatus(ActionOperationStatus.Success, ""))
              case _            ⇒ Future.failed(BadRequestError("Alert not found"))
            }
          case AddLogToTask(content, owner, _, _) ⇒
            for {
              task ← findTaskEntity(entity)
              _ ← logSrv.create(task, Fields.empty.set("message", content).set("owner", owner.map(JsString)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case AddArtifactToCase(data, dataType, dataMessage, _, _) ⇒
            for {
              initialCase ← findCaseEntity(entity)
              artifact ← artifactSrv.create(initialCase.id, Fields.empty.set("data", data).set("dataType", dataType).set("message", dataMessage))
            }  yield operation.updateStatus(ActionOperationStatus.Success, "")
          case AssignCase(ower, _, ) ⇒
            for {
              initialCase ← findCaseEntity(entity)
              caze ← caseSrv.get(initialCase.id)
              _ ← caseSrv.update(caze, Fields.empty.set("owner", owner), ModifyConfig(retryOnConflict = 0, version = Some(caze.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          case AddTagToAlert(tag, _, _) =>
            entity match {
              case initialAlert: Alert ⇒
                for {
                  alert ← alertSrv.get(initialAlert.id)
                  _ ← alertSrv.update(alert.id, Fields.empty.set("tags", Json.toJson((alert.tags() :+ tag).distinct)), ModifyConfig(retryOnConflict = 0, version = Some(alert.version)))
                } yield operation.updateStatus(ActionOperationStatus.Success, "")
              case _ ⇒ Future.failed(BadRequestError("Alert not found"))
            }
          case o ⇒ Future.successful(operation.updateStatus(ActionOperationStatus.Failure, s"Operation $o not supported"))
        }
      }
        .recover {
          case error ⇒
            logger.error("Operation execution fails", error)
            operation.updateStatus(ActionOperationStatus.Failure, error.getMessage)
        }
    }
    else Future.successful(operation)
  }
}
