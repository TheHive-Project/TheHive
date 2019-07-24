package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.actor.ActorRef
import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.Inject
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.{CortexOutputJob, InputCortexAction}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.{Action, ActionContext, ActionOperationStatus, RichAction}
import org.thp.thehive.connector.cortex.services.CortexActor.CheckJob
import org.thp.thehive.models.{EntityHelper, ShareCase, TheHiveSchema}
import org.thp.thehive.services.CaseSteps
import play.api.libs.json.{JsObject, Json, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ActionSrv @Inject()(
    implicit db: Database,
    cortexConfig: CortexConfig,
    implicit val ex: ExecutionContext,
    @Named("cortex-actor") cortexActor: ActorRef,
    actionOperationSrv: ActionOperationSrv,
    schema: TheHiveSchema,
    entityHelper: EntityHelper
) extends VertexSrv[Action, ActionSteps] {

  val actionContextSrv = new EdgeSrv[ActionContext, Action, Product]

  import org.thp.thehive.connector.cortex.controllers.v0.ActionOperationConversion._
  import org.thp.thehive.connector.cortex.controllers.v0.JobConversion._

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ActionSteps = new ActionSteps(raw)

  /**
    * Executes an Action on user demand,
    * creates a job on Cortex side and then persist the
    * Action, looking forward job completion
    *
    * @param inputAction the initial data
    * @param entity the Entity to execute an Action upon
    * @param writes necessary entity json writes
    * @param authContext necessary auth context
    * @return
    */
  def execute(
      inputAction: InputAction,
      entity: Entity
  )(implicit writes: Writes[Entity], authContext: AuthContext): Future[RichAction] =
    for {
      client <- Future.fromTry(Try(cortexConfig.instances(inputAction.cortexId.get)).orElse(Try(cortexConfig.instances.head._2)))
      responder <- client.getResponder(inputAction.responderId).recoverWith {
        case _ => client.getResponderByName(inputAction.responderName.getOrElse(""))
      }

      message    = inputAction.message.getOrElse("")
      parameters = inputAction.parameters.getOrElse(JsObject.empty)
      (tlp, pap) = db.transaction(implicit graph => entityHelper.threatLevels(entity._model.label, entity._id).getOrElse((2, 2)))
      inputCortexAction = InputCortexAction(
        entity._model.label,
        Json.toJson(entity).as[JsObject],
        s"thehive:${inputAction.objectType}",
        tlp,
        pap,
        message,
        parameters.as[JsObject]
      )

      job <- client.execute(responder.id, inputCortexAction)
      action <- Future.fromTry(
        Try(
          db.transaction(implicit graph => create(
            Action.apply(
              job.workerId,
              Some(job.workerName),
              Some(job.workerDefinition),
              fromCortexJobStatus(job.status),
              entity,
              job.startDate.getOrElse(new Date()),
              Some(client.name),
              Some(job.id)
            ),
            entity
          ))
        )
      )

      _ = cortexActor ! CheckJob(None, job.id, Some(action._id), client, authContext)
    } yield action

  /**
    * Creates an Action with necessary ActionContext edge
    *
    * @param action the action to persist
    * @param context the context Entity to link to
    * @param graph graph needed for db queries
    * @param authContext auth for db queries
    * @return
    */
  def create(
      action: Action,
      context: Entity
  )(implicit graph: Graph, authContext: AuthContext): RichAction = {

    val createdAction = create(action)
    actionContextSrv.create(ActionContext(), createdAction, context)

    RichAction(createdAction, context)
  }

  /**
    * Once the job is finished for a precise Action,
    * updates it
    *
    * @param actionId the action to update
    * @param cortexOutputJob the result Cortex job
    * @param authContext context for db queries
    * @return
    */
  def finished(actionId: String, cortexOutputJob: CortexOutputJob)(implicit authContext: AuthContext): Try[Action with Entity] =
    db.transaction { implicit graph =>
      val operations = {
        for {
          report <- cortexOutputJob.report.toSeq
          op     <- report.operations
          if op.status == ActionOperationStatus.Waiting
        } yield {
          for {
            action <- initSteps.get(actionId).richAction.getOrFail()
            operation <- actionOperationSrv.execute(
              action.context,
              op,
              initSteps.get(actionId).relatedCase.getOrFail().toOption
            )
          } yield operation
        }
      } flatMap (_.toOption)

      initSteps
        .get(actionId)
        .update(
          "status"     -> fromCortexJobStatus(cortexOutputJob.status),
          "report"     -> cortexOutputJob.report.map(r => Json.toJson(r.copy(operations = Nil))).getOrElse(JsObject.empty),
          "endDate"    -> new Date(),
          "operations" -> Json.toJson(operations).toString
        )
    }
}

@EntitySteps[Action]
class ActionSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph, schema: Schema) extends BaseVertexSteps[Action, ActionSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ActionSteps = new ActionSteps(raw)

  /**
    * Provides a RichAction model with additional Entity context
    *
    * @return
    */
  def richAction: ScalarSteps[RichAction] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[ActionContext]))
        )
        .map {
          case (action, context) =>
            RichAction(action.as[Action], context.asEntity)
        }
    )

  /**
    * Gets a potential related Case from the Action entity
    *
    * @return
    */
  def relatedCase: CaseSteps =
    new CaseSteps(
      raw.filter(
        _.outTo[ActionContext]
          .outTo[ShareCase]
      )
    )
}
