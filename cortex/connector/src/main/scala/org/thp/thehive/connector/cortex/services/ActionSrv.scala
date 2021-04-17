package org.thp.thehive.connector.cortex.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.structure.Element
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.{InputAction => CortexAction, OutputJob => CortexJob}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.{EntityId, NotFoundError}
import org.thp.thehive.connector.cortex.CortexConnector
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models._
import org.thp.thehive.connector.cortex.services.ActionOps._
import org.thp.thehive.connector.cortex.services.Conversion._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{LogSrv, OrganisationSrv}
import play.api.libs.json.{JsObject, Json, OWrites}

import java.util.{Date, Map => JMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ActionSrv(
    _cortexActor: => ActorRef @@ CortexTag,
    actionOperationSrv: ActionOperationSrv,
    entityHelper: EntityHelper,
    serviceHelper: ServiceHelper,
    logSrv: LogSrv,
    implicit val schema: Schema,
    implicit val db: Database,
    implicit val ec: ExecutionContext,
    auditSrv: CortexAuditSrv
) extends VertexSrv[Action] {

  lazy val cortexActor: ActorRef @@ CortexTag = _cortexActor
  val actionContextSrv                        = new EdgeSrv[ActionContext, Action, Product]

  /**
    * Executes an Action on user demand,
    * creates a job on Cortex side and then persist the
    * Action, looking forward job completion
    *
    * @param entity      the Entity to execute an Action upon
    * @param authContext necessary auth context
    * @return
    */
  def execute(entity: Product with Entity, cortexId: Option[String], workerId: String, parameters: JsObject)(implicit
      writes: OWrites[Entity],
      authContext: AuthContext
  ): Future[RichAction] = {
    val cortexClients = serviceHelper.availableCortexClients(CortexConnector.clients, authContext.organisation)
    for {
      client <- cortexId match {
        case Some(cortexId) =>
          cortexClients
            .find(_.name == cortexId)
            .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
        case None if cortexClients.nonEmpty =>
          Future
            .traverse(cortexClients) { client =>
              client.getResponder(workerId).map(_ => Some(client)).recover { case _ => None }
            }
            .flatMap(
              _.flatten.headOption.fold[Future[CortexClient]](Future.failed(NotFoundError(s"Responder $workerId not found")))(Future.successful)
            )

        case None => Future.failed(NotFoundError(s"Responder $workerId not found"))
      }
      (label, tlp, pap) <- Future.fromTry(db.roTransaction(implicit graph => entityHelper.entityInfo(entity)))
      inputCortexAction = CortexAction(label, writes.writes(entity), s"thehive:${fromObjectType(entity._label)}", tlp, pap, parameters)
      job <- client.execute(workerId, inputCortexAction)
      action = Action(
        job.workerId,
        job.workerName,
        job.workerDefinition,
        job.status.toJobStatus,
        parameters: JsObject,
        new Date,
        job.endDate: Option[Date],
        job.report.flatMap(_.full),
        client.name,
        job.id,
        job.report.fold[Seq[JsObject]](Nil)(_.operations)
      )
      createdAction <- Future.fromTry {
        db.tryTransaction { implicit graph =>
          for {
            richAction <- create(action, entity)
            auditContext = entity._label match {
              case "Log" => logSrv.get(entity).task.headOption.getOrElse(entity)
              case _     => entity
            }
            _ <- auditSrv.action.create(richAction.action, auditContext, richAction.toJson)
          } yield richAction
        }
      }
      _ = cortexActor ! CheckJob(None, job.id, Some(createdAction._id), client.name, authContext)
    } yield createdAction
  }

  /**
    * Creates an Action with necessary ActionContext edge
    *
    * @param action      the action to persist
    * @param context     the context Entity to link to
    * @param graph       graph needed for db queries
    * @param authContext auth for db queries
    * @return
    */
  def create(
      action: Action,
      context: Product with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[RichAction] =
    for {
      createdAction <- createEntity(action)
      _             <- actionContextSrv.create(ActionContext(), createdAction, context)
    } yield RichAction(createdAction, context)

  /**
    * Once the job is finished for a precise Action,
    * updates it
    *
    * @param actionId    the action to update
    * @param cortexJob   the result Cortex job
    * @param authContext context for db queries
    * @return
    */
  def finished(actionId: EntityId, cortexJob: CortexJob)(implicit authContext: AuthContext): Try[Action with Entity] =
    db.tryTransaction { implicit graph =>
      getByIds(actionId).richAction.getOrFail("Action").flatMap { action =>
        val operations: Seq[ActionOperationStatus] = cortexJob
          .report
          .fold[Seq[ActionOperation]](Nil)(_.operations.map(_.as[ActionOperation]))
          .map { operation =>
            actionOperationSrv
              .execute(
                action.context,
                operation,
                relatedCase(actionId),
                relatedTask(actionId)
              )
              .fold(t => ActionOperationStatus(operation, success = false, t.getMessage), identity)
          }
        val auditContext = action.context._label match {
          case "Log" => logSrv.get(action.context).task.headOption.getOrElse(action.context)
          case _     => action.context
        }

        getByIds(actionId)
          .update(_.status, cortexJob.status.toJobStatus)
          .update(_.report, cortexJob.report.map(r => Json.toJsObject(r.copy(operations = Nil))))
          .update(_.endDate, Some(new Date()))
          .update(_.operations, operations.map(o => Json.toJsObject(o)))
          .getOrFail("Action")
          .map { updated =>
            auditSrv
              .action
              .update(
                updated,
                auditContext,
                Json.obj(
                  "status"        -> updated.status.toString,
                  "objectId"      -> action.context._id,
                  "objectType"    -> action.context._label,
                  "responderName" -> action.workerName
                )
              )

            updated
          }
      }
    }

  /**
    * Gets an optional related Case to the Action Entity
    * @param id action id
    * @param graph db graph
    * @return
    */
  def relatedCase(id: EntityId)(implicit graph: Graph): Option[Case with Entity] =
    for {
      richAction  <- startTraversal.getByIds(id).richAction.getOrFail("Action").toOption
      relatedCase <- entityHelper.parentCase(richAction.context)
    } yield relatedCase

  def relatedTask(id: EntityId)(implicit graph: Graph): Option[Task with Entity] =
    for {
      richAction  <- startTraversal.getByIds(id).richAction.getOrFail("Action").toOption
      relatedTask <- entityHelper.parentTask(richAction.context)
    } yield relatedTask

  // TODO to be tested
  def listForEntity(id: EntityId)(implicit graph: Graph): Seq[RichAction] = startTraversal.forEntity(id).richAction.toSeq
}

object ActionOps {

  implicit class ActionOpsDefs(traversal: Traversal.V[Action]) {

    /**
      * Provides a RichAction model with additional Entity context
      *
      * @return
      */
    def richAction: Traversal[RichAction, JMap[String, Any], Converter[RichAction, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.out[ActionContext].entity)
        )
        .domainMap {
          case (action, context) =>
            RichAction(action, context)
        }

    def forEntity(entityId: EntityId): Traversal.V[Action] =
      traversal.filter(_.out[ActionContext].hasId(entityId))

    def context: Traversal[Product with Entity, Element, Converter[Product with Entity, Element]] = traversal.out[ActionContext].entity

    def visible(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Action] =
      traversal.filter(
        _.out[ActionContext]
          .chooseBranch[String, Any](
            _.on(_.label)
              .option("Case", _.v[Case].visible(organisationSrv).widen[Any])
              .option("Task", _.v[Task].visible(organisationSrv).widen[Any])
              .option("Log", _.v[Log].visible(organisationSrv).widen[Any])
              .option("Alert", _.v[Alert].visible(organisationSrv).widen[Any])
              .option("Observable", _.v[Observable].visible(organisationSrv).widen[Any])
          )
      )
  }
}
