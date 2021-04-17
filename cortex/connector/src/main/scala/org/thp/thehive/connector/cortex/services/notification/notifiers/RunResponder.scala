package org.thp.thehive.connector.cortex.services.notification.notifiers

import com.typesafe.config.ConfigRenderOptions
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadConfigurationError, NotFoundError, RichOption}
import org.thp.thehive.connector.cortex.services.{ActionSrv, ResponderSrv}
import org.thp.thehive.controllers.v0.AuditRenderer
import org.thp.thehive.models.{Audit, Organisation, Permissions, User}
import org.thp.thehive.services._
import org.thp.thehive.services.notification.notifiers.{Notifier, NotifierProvider}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json, OWrites}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class RunResponderProvider(
    responderSrv: ResponderSrv,
    actionSrv: ActionSrv,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    ec: ExecutionContext
) extends NotifierProvider {
  override val name: String = "RunResponder"

  override def apply(config: Configuration): Try[Notifier] = {

    val parameters = Try(Json.parse(config.underlying.getValue("parameters").render(ConfigRenderOptions.concise())).as[JsObject]).toOption
    config.getOrFail[String]("responderName").map { responderName =>
      new RunResponder(
        responderName,
        parameters.getOrElse(JsObject.empty),
        responderSrv,
        actionSrv,
        taskSrv,
        caseSrv,
        observableSrv,
        logSrv,
        alertSrv,
        ec
      )
    }
  }
}

class RunResponder(
    responderName: String,
    parameters: JsObject,
    responderSrv: ResponderSrv,
    actionSrv: ActionSrv,
    taskSrv: TaskSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    implicit val ec: ExecutionContext
) extends Notifier
    with AuditRenderer {
  override val name: String = "RunResponder"

  def getEntity(audit: Audit)(implicit graph: Graph): Try[(Product with Entity, JsObject)] =
    audit
      .objectEntityId
      .flatMap { objectId =>
        audit.objectType.map {
          case "Task"       => taskSrv.get(objectId).project(_.by.by(taskToJson)).getOrFail("Task")
          case "Case"       => caseSrv.get(objectId).project(_.by.by(caseToJson)).getOrFail("Case")
          case "Observable" => observableSrv.get(objectId).project(_.by.by(observableToJson)).getOrFail("Observable")
          case "Log"        => logSrv.get(objectId).project(_.by.by(logToJson)).getOrFail("Log")
          case "Alert"      => alertSrv.get(objectId).project(_.by.by(alertToJson)).getOrFail("Alert")
          case objectType   => Failure(NotFoundError(s"objectType $objectType is not recognised"))
        }
      }
      .getOrElse(Failure(NotFoundError("Object not present in the audit")))

  override def execute(
      audit: Audit with Entity,
      context: Option[Entity],
      `object`: Option[Entity],
      organisation: Organisation with Entity,
      user: Option[User with Entity]
  )(implicit graph: Graph): Future[Unit] =
    if (user.isDefined)
      Future.failed(BadConfigurationError("The notification runResponder must not be applied on user"))
    else
      for {
        (entity, entityJson) <- Future.fromTry(getEntity(audit))
        workers              <- responderSrv.getRespondersByName(responderName, organisation._id)
        (worker, cortexIds)  <- Future.fromTry(workers.headOption.toTry(Failure(NotFoundError(s"Responder $responderName not found"))))
        authContext = LocalUserSrv.getSystemAuthContext.changeOrganisation(organisation._id, Permissions.all)
        _ <- actionSrv.execute(entity, cortexIds.headOption, worker.id, parameters)(OWrites[Entity](_ => entityJson), authContext)
      } yield ()
}
