package org.thp.thehive.models
import java.util.Date

import gremlin.scala.{Edge, Graph, Vertex}
import org.thp.scalligraph.controllers.UpdateOps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.AttachmentSrv
import org.thp.scalligraph.{FPath, VertexEntity}

import scala.concurrent.{ExecutionContext, Future}

object AuditableAction extends Enumeration {
  val Update, Creation, Delete, Get = Value
}

@VertexEntity
case class Audit(
    operation: AuditableAction.Value,
    requestId: String,
    attributeName: Option[String],
    oldValue: Option[String],
    newValue: Option[String])

case class RichAudit(
    _id: String,
    _createdAt: Date,
    _createdBy: String,
    operation: AuditableAction.Value,
    requestId: String,
    attributeName: Option[String],
    oldValue: Option[String],
    newValue: Option[String],
    obj: Entity,
    summary: Map[String, Map[AuditableAction.Value, Int]]
)

object RichAudit {
  def apply(audit: Audit with Entity, obj: Entity, summary: Map[String, Map[AuditableAction.Value, Int]]): RichAudit =
    new RichAudit(
      audit._id,
      audit._createdAt,
      audit._createdBy,
      audit.operation,
      audit.requestId,
      audit.attributeName,
      audit.oldValue,
      audit.newValue,
      obj,
      summary)
}

case class Audited()

object Audited extends HasEdgeModel[Audited, Audit, Product] {

  override val model: Model.Edge[Audited, Audit, Product] = new EdgeModel[Audit, Product] { thisModel ⇒
    override type E = Audited
    override val label: String                                                                                            = "Audited"
    override val fromLabel: String                                                                                        = "Audit"
    override val toLabel: String                                                                                          = ""
    override val indexes: Seq[(IndexType.Value, Seq[String])]                                                             = Nil
    override def saveAttachment(attachmentSrv: AttachmentSrv, e: Audited)(implicit ec: ExecutionContext): Future[Audited] = Future.successful(e)
    override def saveUpdateAttachment(attachmentSrv: AttachmentSrv, updates: Map[FPath, UpdateOps.Type])(
        implicit ec: ExecutionContext): Future[Map[FPath, UpdateOps.Type]] = Future.successful(updates)

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
