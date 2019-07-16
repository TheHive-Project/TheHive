package org.thp.thehive.connector.cortex.services

import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.Inject
import org.thp.cortex.client.CortexConfig
import org.thp.cortex.dto.v0.InputCortexAction
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.Action
import org.thp.thehive.models.Case
import play.api.libs.json.{JsObject, Json, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ActionSrv @Inject()(
    implicit db: Database,
    cortexConfig: CortexConfig,
    implicit val ex: ExecutionContext
) extends VertexSrv[Action, ActionSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ActionSteps = new ActionSteps(raw)

  def execute(
               inputAction: InputAction,
               entity: Entity,
               relatedCase: Option[Case with Entity])(implicit writes: Writes[Entity]) = {
    for {
      client <- Future.fromTry(Try(cortexConfig.instances(inputAction.cortexId.get)).orElse(Try(cortexConfig.instances.head._2)))
      responder <- client.getResponder(inputAction.responderId).recoverWith { case _ => client.getResponderByName(inputAction.responderName) }
      message = inputAction.message.getOrElse("")
      parameters = inputAction.parameters.getOrElse(JsObject.empty)
      tlp = inputAction.tlp.orElse(relatedCase.map(_.tlp)).getOrElse(2)
      pap = relatedCase.map(_.pap).getOrElse(2)
      inputCortexAction = InputCortexAction(entity._model.label, Json.toJson(entity).as[JsObject], s"thehive:${inputAction.objectType}", tlp, pap, message, parameters)
      job <- client.execute(responder.id, inputCortexAction)
    }
  }
}

@EntitySteps[Action]
class ActionSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Action, ActionSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ActionSteps = new ActionSteps(raw)
}
