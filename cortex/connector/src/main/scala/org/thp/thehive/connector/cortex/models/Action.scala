package org.thp.thehive.connector.cortex.models

import java.util.Date

import play.api.libs.json.JsObject

import gremlin.scala.{Edge, Graph, Vertex}
import org.thp.scalligraph.VertexEntity
import org.thp.scalligraph.models._

@VertexEntity
case class Action(
    workerId: String,
    workerName: String,
    workerDefinition: String,
    status: JobStatus.Value,
    parameters: JsObject,
    startDate: Date,
    endDate: Option[Date],
    report: Option[JsObject],
    cortexId: String,
    cortexJobId: String,
    operations: Seq[JsObject]
)

case class RichAction(action: Action with Entity, context: Entity) {
  def _id: String               = action._id
  def _createdAt: Date          = action._createdAt
  def _createdBy: String        = action._createdBy
  def workerId: String          = action.workerId
  def workerName: String        = action.workerName
  def workerDefinition: String  = action.workerDefinition
  def status: JobStatus.Value   = action.status
  def startDate: Date           = action.startDate
  def endDate: Option[Date]     = action.endDate
  def report: Option[JsObject]  = action.report
  def cortexId: String          = action.cortexId
  def cortexJobId: String       = action.cortexJobId
  def operations: Seq[JsObject] = action.operations
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
      override val _id: String                = element.id().toString
      override val _model: Model              = thisModel
      override val _createdBy: String         = db.getProperty(element, "_createdBy", UniMapping.string)
      override val _updatedBy: Option[String] = db.getProperty(element, "_updatedBy", UniMapping.string.optional)
      override val _createdAt: Date           = db.getProperty(element, "_createdAt", UniMapping.date)
      override val _updatedAt: Option[Date]   = db.getProperty(element, "_updatedAt", UniMapping.date.optional)
    }
    override def addEntity(a: ActionContext, entity: Entity): EEntity = new ActionContext with Entity {
      override def _id: String                = entity._id
      override def _model: Model              = entity._model
      override def _createdBy: String         = entity._createdBy
      override def _updatedBy: Option[String] = entity._updatedBy
      override def _createdAt: Date           = entity._createdAt
      override def _updatedAt: Option[Date]   = entity._updatedAt
    }
    override def create(e: ActionContext, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge = from.addEdge(label, to)
  }
}
