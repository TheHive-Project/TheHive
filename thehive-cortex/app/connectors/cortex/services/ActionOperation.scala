package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future }

import play.api.libs.json._

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import javax.inject.{ Inject, Singleton }
import models.{ Alert, Case, Artifact, Task, TaskStatus }
import services.{ CaseSrv, TaskSrv, ArtifactSrv }

import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.models.{ BaseEntity, ChildModelDef, HiveEnumeration }
import org.elastic4play.services.{ AuthContext, FindSrv }
import org.elastic4play.utils.RetryOnError
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
  def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddTagToCase = copy(status = newStatus, message = newMessage)
}

case class AddTagToArtifact(tag: String, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): AddTagToArtifact = copy(status = newStatus, message = newMessage)
}

case class CreateTask(fields: JsObject, status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): CreateTask = copy(status = newStatus, message = newMessage)
}

case class CloseTask(status: ActionOperationStatus.Type = ActionOperationStatus.Waiting, message: String = "") extends ActionOperation {
  def updateStatus(newStatus: ActionOperationStatus.Type, newMessage: String): CloseTask = copy(status = newStatus, message = newMessage)
}

object ActionOperation {
  val addTagToCaseWrites = Json.writes[AddTagToCase]
  val addTagToArtifactWrites = Json.writes[AddTagToArtifact]
  val createTaskWrites = Json.writes[CreateTask]
  val closeTaskWrites = Json.writes[CloseTask]
  implicit val actionOperationReads: Reads[ActionOperation] = Reads[ActionOperation](json ⇒
    (json \ "type").asOpt[String].fold[JsResult[ActionOperation]](JsError("type is missing in action operation")) {
      case "AddTagToCase"     ⇒ (json \ "tag").validate[String].map(tag ⇒ AddTagToCase(tag))
      case "AddTagToArtifact" ⇒ (json \ "tag").validate[String].map(tag ⇒ AddTagToArtifact(tag))
      case "CreateTask"       ⇒ JsSuccess(CreateTask(json.as[JsObject] - "type"))
      case "CloseTask"        ⇒ JsSuccess(CloseTask())
      case other              ⇒ JsError(s"Unknown operation $other")
    })
  implicit val actionOperationWrites: Writes[ActionOperation] = Writes[ActionOperation] {
    case a: AddTagToCase     ⇒ addTagToCaseWrites.writes(a)
    case a: AddTagToArtifact ⇒ addTagToArtifactWrites.writes(a)
    case a: CreateTask       ⇒ createTaskWrites.writes(a)
    case a: CloseTask        ⇒ closeTaskWrites.writes(a)
    case a                   ⇒ Json.obj("unsupported operation" → a.toString)
  }
}

@Singleton
class ActionOperationSrv @Inject() (
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    findSrv: FindSrv,
    artifactSrv: ArtifactSrv,
    implicit val system: ActorSystem,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def findCaseEntity(entity: BaseEntity): Future[Case] = {
    import org.elastic4play.services.QueryDSL._

    (entity, entity.model) match {
      case (c: Case, _)  ⇒ Future.successful(c)
      case (a: Alert, _) ⇒ a.caze().fold(Future.failed[Case](BadRequestError("Alert hasn't been imported to case")))(caseSrv.get)
      case (_, model: ChildModelDef[_, _, _, _]) ⇒
        findSrv(model.parentModel, "_id" ~= entity.parentId.getOrElse(throw InternalError(s"Child entity $entity has no parent ID")), Some("0-1"), Nil)
          ._1.runWith(Sink.head).flatMap(findCaseEntity)
      case _ ⇒ Future.failed(BadRequestError("Case not found"))
    }
  }

  def findArtifactEntity(entity: BaseEntity): Future[Artifact] = {
    (entity, entity.model) match {
      case (a: Artifact, _) ⇒ Future.successful(a)
      case _                ⇒ Future.failed(BadRequestError("Artifact not found"))
    }
  }

  def findTaskEntity(entity: BaseEntity): Future[Task] = {
    (entity, entity.model) match {
      case (a: Task, _) ⇒ Future.successful(a)
      case _            ⇒ Future.failed(BadRequestError("Task not found"))
    }
  }

  def execute(entity: BaseEntity, operation: ActionOperation)(implicit authContext: AuthContext): Future[ActionOperation] = {
    if (operation.status == ActionOperationStatus.Waiting) {
      val updatedOperation = operation match {
        case AddTagToCase(tag, _, _) ⇒
          RetryOnError() { // FIXME find the right exception
            for {
              initialCase ← findCaseEntity(entity)
              caze ← caseSrv.get(initialCase.id)
              _ ← caseSrv.update(caze, Fields.empty.set("tags", Json.toJson((caze.tags() :+ tag).distinct)), ModifyConfig(retryOnConflict = 0, version = Some(caze.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          }
        case AddTagToArtifact(tag, _, _) ⇒
          RetryOnError() { // FIXME find the right exception
            for {
              initialArtifact ← findArtifactEntity(entity)
              art ← artifactSrv.get(initialArtifact.artifactId())
              _ ← artifactSrv.update(art.artifactId(), Fields.empty.set("tags", Json.toJson((art.tags() :+ tag).distinct)), ModifyConfig(retryOnConflict = 0, version = Some(art.version)))
            } yield operation.updateStatus(ActionOperationStatus.Success, "")
          }
        case CreateTask(fields, _, _) ⇒
          for {
            caze ← findCaseEntity(entity)
            _ ← taskSrv.create(caze, Fields(fields))
          } yield operation.updateStatus(ActionOperationStatus.Success, "")
        case CloseTask(_, _) ⇒
          for {
            initialTask ← findTaskEntity(entity)
            task ← taskSrv.get(initialTask.id)
            _ ← taskSrv.update(task, Fields.empty.set("status", TaskStatus.Completed.toString).set("flag", JsFalse), ModifyConfig(retryOnConflict = 0, version = Some(task.version)))
          } yield operation.updateStatus(ActionOperationStatus.Success, "")
        case o ⇒ Future.successful(operation.updateStatus(ActionOperationStatus.Failure, s"Operation $o not supported"))
      }
      updatedOperation.recover { case error ⇒ operation.updateStatus(ActionOperationStatus.Failure, error.getMessage) }
    }
    else Future.successful(operation)
  }
}
