package org.thp.thehive.connector.cortex.models

import java.util.Date
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.{Converter, Graph}
import org.thp.scalligraph.{BuildVertexEntity, EntityId}
import play.api.libs.json.JsObject

@BuildVertexEntity
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

case class RichAction(action: Action with Entity, context: Product with Entity) {
  def _id: EntityId             = action._id
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

object ActionContext extends HasModel {

  override val model: Model.Edge[ActionContext] = new EdgeModel {
    override type E = ActionContext
    override val label: String                                = "ActionContext"
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override val converter: Converter[EEntity, Edge] = (element: Edge) =>
      new ActionContext with Entity {
        override val _id: EntityId              = EntityId(element.id())
        override val _label: String             = "ActionContext"
        override val _createdBy: String         = UMapping.string.getProperty(element, "_createdBy")
        override val _updatedBy: Option[String] = UMapping.string.optional.getProperty(element, "_updatedBy")
        override val _createdAt: Date           = UMapping.date.getProperty(element, "_createdAt")
        override val _updatedAt: Option[Date]   = UMapping.date.optional.getProperty(element, "_updatedAt")
      }
    override def addEntity(a: ActionContext, entity: Entity): EEntity =
      new ActionContext with Entity {
        override def _id: EntityId              = entity._id
        override def _label: String             = entity._label
        override def _createdBy: String         = entity._createdBy
        override def _updatedBy: Option[String] = entity._updatedBy
        override def _createdAt: Date           = entity._createdAt
        override def _updatedAt: Option[Date]   = entity._updatedAt
      }
    override def create(e: ActionContext, from: Vertex, to: Vertex)(implicit graph: Graph): Edge =
      from.addEdge(label, to)
  }
}
