package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{EntityIdOrName, InternalError}
import org.thp.thehive.connector.cortex.models._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger

import java.util.Date
import scala.util.{Failure, Success, Try}

class ActionOperationSrv(
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    taskSrv: TaskSrv,
    alertSrv: AlertSrv,
    logSrv: LogSrv,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) {
  private[ActionOperationSrv] lazy val logger: Logger = Logger(getClass)

  /**
    * Executes an operation from Cortex responder
    * report
    * @param entity the entity concerned by the operation
    * @param operation the operation to execute
    * @param relatedCase the related case if applicable
    * @param graph graph traversal
    * @param authContext auth for access check
    * @return
    */
  def execute(entity: Entity, operation: ActionOperation, relatedCase: Option[Case with Entity], relatedTask: Option[Task with Entity])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[ActionOperationStatus] = {

    def updateOperation(operation: ActionOperation) = ActionOperationStatus(operation, success = true, "Success")

    operation match {
      case AddTagToCase(tag) =>
        for {
          c <- relatedCase.fold[Try[Case with Entity]](Failure(InternalError("Unable to apply action AddTagToCase without case")))(Success(_))
          _ <- caseSrv.addTags(c, Set(tag))
        } yield updateOperation(operation)

      case AddTagToArtifact(tag) =>
        for {
          obs <- observableSrv.getOrFail(entity._id)
          _   <- observableSrv.addTags(obs, Set(tag))
        } yield updateOperation(operation)

      case CreateTask(title, description) =>
        for {
          case0 <- relatedCase.fold[Try[Case with Entity]](Failure(InternalError("Unable to apply action CreateTask without case")))(Success(_))
          _     <- caseSrv.createTask(case0, InputTask(title = title, description = Some(description)).toTask)
        } yield updateOperation(operation)

      case AddCustomFields(name, _, value) =>
        for {
          c <- relatedCase.fold[Try[Case with Entity]](Failure(InternalError("Unable to apply action AddCustomFields without case")))(Success(_))
          _ <- caseSrv.setOrCreateCustomField(c, EntityIdOrName(name), Some(value), None)
        } yield updateOperation(operation)

      case CloseTask() =>
        for {
          t <- relatedTask.fold[Try[Task with Entity]](Failure(InternalError("Unable to apply action CloseTask without task")))(Success(_))
          _ <- taskSrv.get(t).update(_.status, TaskStatus.Completed).getOrFail("Task")
        } yield updateOperation(operation)

      case MarkAlertAsRead() =>
        entity._label match {
          case "Alert" => alertSrv.markAsRead(entity._id).map(_ => updateOperation(operation))
          case x       => Failure(new Exception(s"Wrong entity for MarkAlertAsRead: ${x.getClass}"))
        }

      case AddLogToTask(content, _) =>
        for {
          t <- relatedTask.fold[Try[Task with Entity]](Failure(InternalError("Unable to apply action AddLogToTask without task")))(Success(_))
          _ <- logSrv.create(Log(content, new Date()), t, None)
        } yield updateOperation(operation)

      case AddArtifactToCase(data, dataType, message) =>
        for {
          c            <- relatedCase.fold[Try[Case with Entity]](Failure(InternalError("Unable to apply action AddArtifactToCase without case")))(Success(_))
          organisation <- organisationSrv.getOrFail(authContext.organisation)
          _ <- caseSrv.createObservable(
            c,
            Observable(
              message = Some(message),
              tlp = 2,
              ioc = false,
              sighted = false,
              ignoreSimilarity = None,
              dataType = dataType,
              tags = Nil,
              relatedId = c._id,
              organisationIds = Set(organisation._id)
            ),
            data
          )
        } yield updateOperation(operation)

      case AssignCase(owner) =>
        for {
          c <- relatedCase.fold[Try[Case with Entity]](Failure(InternalError("Unable to apply action AssignCase without case")))(Success(_))
          u <- userSrv.get(EntityIdOrName(owner)).getOrFail("User")
          _ <- caseSrv.unassign(c)
          _ <- caseSrv.assign(c, u)
        } yield updateOperation(operation)

      case AddTagToAlert(tag) =>
        entity._label match {
          case "Alert" => alertSrv.get(entity).getOrFail("Alert").flatMap(alertSrv.addTags(_, Set(tag)).map(_ => updateOperation(operation)))
          case x       => Failure(new Exception(s"Wrong entity for AddTagToAlert: ${x.getClass}"))
        }

      case x =>
        val m = s"ActionOperation ${x.toString} unknown"
        logger.error(m)
        Failure(new Exception(m))
    }
  } recover {
    case e =>
      logger.error("Operation execution fails", e)
      ActionOperationStatus(operation, success = false, e.getMessage)
  }
}
