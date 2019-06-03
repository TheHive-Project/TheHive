package org.thp.thehive.models

import java.util.Date

import gremlin.scala.{Edge, Graph, Vertex}
import org.thp.scalligraph.models._
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import org.thp.thehive.services.EventMessage

@EdgeEntity[Audit, User]
case class AuditUser()

@VertexEntity
case class Audit(
    requestId: String,
    action: String,
    objectId: Option[String],
    objectType: Option[String],
    details: Option[String]
)

case class RichAudit(
    _id: String,
    _createdAt: Date,
    _createdBy: String,
    action: String,
    requestId: String,
    details: Option[String],
    context: Entity,
    `object`: Option[Entity]
) extends EventMessage

object RichAudit {

  def apply(audit: Audit with Entity, context: Entity, `object`: Option[Entity]): RichAudit =
    new RichAudit(
      audit._id,
      audit._createdAt,
      audit._createdBy,
      audit.action,
      audit.requestId,
      audit.details,
      context,
      `object`
    )
}

case class Audited()

object Audited extends HasEdgeModel[Audited, Audit, Product] {

  override val model: Model.Edge[Audited, Audit, Product] = new EdgeModel[Audit, Product] { thisModel ⇒
    override type E = Audited
    override val label: String                                = "Audited"
    override val fromLabel: String                            = "Audit"
    override val toLabel: String                              = ""
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override def toDomain(element: Edge)(implicit db: Database): Audited with Entity = new Audited with Entity {
      override val _id: String                = element.value[String]("_id")
      override val _model: Model              = thisModel
      override val _createdBy: String         = db.getProperty(element, "_createdBy", UniMapping.stringMapping)
      override val _updatedBy: Option[String] = db.getProperty(element, "_updatedBy", UniMapping.stringMapping.optional)
      override val _createdAt: Date           = db.getProperty(element, "_createdAt", UniMapping.dateMapping)
      override val _updatedAt: Option[Date]   = db.getProperty(element, "_updatedAt", UniMapping.dateMapping.optional)
    }
    override def create(e: Audited, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge = from.addEdge(label, to)
  }
}
case class AuditContext()

object AuditContext extends HasEdgeModel[AuditContext, Audit, Product] {

  override val model: Model.Edge[AuditContext, Audit, Product] = new EdgeModel[Audit, Product] { thisModel ⇒
    override type E = AuditContext
    override val label: String                                = "Audited"
    override val fromLabel: String                            = "Audit"
    override val toLabel: String                              = ""
    override val indexes: Seq[(IndexType.Value, Seq[String])] = Nil

    override val fields: Map[String, Mapping[_, _, _]] = Map.empty
    override def toDomain(element: Edge)(implicit db: Database): AuditContext with Entity = new AuditContext with Entity {
      override val _id: String                = element.value[String]("_id")
      override val _model: Model              = thisModel
      override val _createdBy: String         = db.getProperty(element, "_createdBy", UniMapping.stringMapping)
      override val _updatedBy: Option[String] = db.getProperty(element, "_updatedBy", UniMapping.stringMapping.optional)
      override val _createdAt: Date           = db.getProperty(element, "_createdAt", UniMapping.dateMapping)
      override val _updatedAt: Option[Date]   = db.getProperty(element, "_updatedAt", UniMapping.dateMapping.optional)
    }
    override def create(e: AuditContext, from: Vertex, to: Vertex)(implicit db: Database, graph: Graph): Edge = from.addEdge(label, to)
  }
}
