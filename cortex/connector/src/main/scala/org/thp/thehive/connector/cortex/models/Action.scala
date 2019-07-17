package org.thp.thehive.connector.cortex.models

import java.util.Date

import gremlin.scala.{Edge, Graph, Vertex}
import org.thp.scalligraph.VertexEntity
import org.thp.scalligraph.models._
import play.api.libs.json.JsObject

@VertexEntity
case class Action(
    responderId: String,
    responderName: Option[String],
    responderDefinition: Option[String],
    status: JobStatus.Value,
    objectType: String,
    objectId: String,
    startDate: Date,
    endDate: Option[Date],
    report: Option[JsObject],
    cortexId: Option[String],
    cortexJobId: Option[String],
    operations: Option[String]
)

object Action {

  def apply(
      responderId: String,
      responderName: Option[String],
      responderDefinition: Option[String],
      status: JobStatus.Value,
      entity: Entity,
      startDate: Date,
      cortexId: Option[String],
      cortexJobId: Option[String]
  ): Action = Action(
    responderId,
    responderName,
    responderDefinition,
    status,
    entity._model.label,
    entity._id,
    startDate,
    None,
    None,
    cortexId,
    cortexJobId,
    None
  )
}

case class ActionContext()

object ActionContext extends HasEdgeModel[ActionContext, Action, Product] {

  override val model: Model.Edge[ActionContext, Action, Product] = new EdgeModel[Action, Product] { thisModel =>
    override type E = ActionContext
    override val label: String                                = "ActionContext"
    override val fromLabel: String                            = "Action"
    override val toLabel: String                              = ""
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override def toDomain(element: Edge)(implicit db: Database): ActionContext with Entity = new ActionContext with Entity {
      override val _id: String                = element.value[String]("_id")
      override val _model: Model              = thisModel
      override val _createdBy: String         = db.getProperty(element, "_createdBy", UniMapping.stringMapping)
      override val _updatedBy: Option[String] = db.getProperty(element, "_updatedBy", UniMapping.stringMapping.optional)
      override val _createdAt: Date           = db.getProperty(element, "_createdAt", UniMapping.dateMapping)
      override val _updatedAt: Option[Date]   = db.getProperty(element, "_updatedAt", UniMapping.dateMapping.optional)
    }
    override def create(e: ActionContext, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge = from.addEdge(label, to)
  }
}
