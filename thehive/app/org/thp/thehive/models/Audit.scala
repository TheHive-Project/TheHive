package org.thp.thehive.models

import java.util.Date

import org.apache.tinkerpop.gremlin.structure.{Edge, Graph, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Converter
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildEdgeEntity[Audit, User]
case class AuditUser()

@DefineIndex(IndexType.basic, "requestId", "mainAction")
@BuildVertexEntity
case class Audit(
    requestId: String,
    action: String,
    mainAction: Boolean,
    objectId: Option[String],
    objectType: Option[String],
    details: Option[String]
)

object Audit {

  def apply(action: String, entity: Entity, details: Option[String] = None)(implicit authContext: AuthContext): Audit =
    Audit(authContext.requestId, action, mainAction = false, Some(entity._id), Some(entity._label), details)

  final val create = "create"
  final val update = "update"
  final val delete = "delete"
  final val merge  = "merge"
}

case class RichAudit(
    _id: String,
    _createdAt: Date,
    _createdBy: String,
    action: String,
    mainAction: Boolean,
    requestId: String,
    objectId: Option[String],
    objectType: Option[String],
    details: Option[String],
    context: Entity,
    visibilityContext: Entity,
    `object`: Option[Entity]
)

object RichAudit {

  def apply(
      audit: Audit with Entity,
      context: Product with Entity,
      visibilityContext: Product with Entity,
      `object`: Option[Product with Entity]
  ): RichAudit =
    new RichAudit(
      audit._id,
      audit._createdAt,
      audit._createdBy,
      audit.action,
      audit.mainAction,
      audit.requestId,
      audit.objectId,
      audit.objectType,
      audit.details,
      context,
      visibilityContext,
      `object`
    )
}

case class Audited()

object Audited {

  val model: Model.Edge[Audited] = new EdgeModel { thisModel =>
    override type E = Audited
    override val label: String                                = "Audited"
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override val converter: Converter[EEntity, Edge] = (element: Edge) =>
      new Audited with Entity {
        override val _id: String                = element.id().toString
        override val _label: String             = "Audited"
        override val _createdBy: String         = UMapping.string.getProperty(element, "_createdBy")
        override val _updatedBy: Option[String] = UMapping.string.optional.getProperty(element, "_updatedBy")
        override val _createdAt: Date           = UMapping.date.getProperty(element, "_createdAt")
        override val _updatedAt: Option[Date]   = UMapping.date.optional.getProperty(element, "_updatedAt")
      }
    override def addEntity(a: Audited, entity: Entity): EEntity = new Audited with Entity {
      override def _id: String                = entity._id
      override def _label: String             = entity._label
      override def _createdBy: String         = entity._createdBy
      override def _updatedBy: Option[String] = entity._updatedBy
      override def _createdAt: Date           = entity._createdAt
      override def _updatedAt: Option[Date]   = entity._updatedAt
    }
    override def create(e: Audited, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge = from.addEdge(label, to)
  }
}
case class AuditContext()

object AuditContext extends HasModel {

  override val model: Model.Edge[AuditContext] = new EdgeModel { thisModel =>
    override type E = AuditContext
    override val label: String                                = "AuditContext"
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override val converter: Converter[EEntity, Edge] = (element: Edge) =>
      new AuditContext with Entity {
        override val _id: String                = element.id().toString
        override val _label: String             = "AuditContext"
        override val _createdBy: String         = UMapping.string.getProperty(element, "_createdBy")
        override val _updatedBy: Option[String] = UMapping.string.optional.getProperty(element, "_updatedBy")
        override val _createdAt: Date           = UMapping.date.getProperty(element, "_createdAt")
        override val _updatedAt: Option[Date]   = UMapping.date.optional.getProperty(element, "_updatedAt")
      }
    override def addEntity(a: AuditContext, entity: Entity): EEntity = new AuditContext with Entity {
      override def _id: String                = entity._id
      override def _label: String             = entity._label
      override def _createdBy: String         = entity._createdBy
      override def _updatedBy: Option[String] = entity._updatedBy
      override def _createdAt: Date           = entity._createdAt
      override def _updatedAt: Option[Date]   = entity._updatedAt
    }
    override def create(e: AuditContext, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge =
      from.addEdge(label, to)
  }
}
