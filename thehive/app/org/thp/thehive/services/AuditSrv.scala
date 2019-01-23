package org.thp.thehive.services
import java.util.Date

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models.{Audit, Audited, RichAudit}

@Singleton
class AuditSrv @Inject()()(implicit db: Database, schema: Schema) extends VertexSrv[Audit, AuditSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AuditSteps = new AuditSteps(raw)

  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Entity] =
    get(audit).getObject
}

@EntitySteps[Audit]
class AuditSteps(raw: GremlinScala[Vertex])(implicit db: Database, schema: Schema, graph: Graph) extends BaseVertexSteps[Audit, AuditSteps](raw) {

  override def newInstance(raw: GremlinScala[Vertex]): AuditSteps = new AuditSteps(raw)

  def list: GremlinScala[RichAudit] =
    raw
      .order(By(Key[Date]("_createdAt"), Order.incr)) // Order.asc is not recognized by org.janusgraph.graphdb.internal.Order.convert
      .value[String]("requestId")
      .dedup()
      .map(requestId ⇒ richAudit(requestId))

  def richAudit(requestId: String): RichAudit = {
    val auditList = graph
      .V()
      .has("Audit", Key[String]("requestId"), requestId)
      .order(By(Key[Date]("_createdAt"), Order.incr)) // Order.asc is not recognized by org.janusgraph.graphdb.internal.Order.convert
      .project[Any]("audit", "object")
      .by()
      .by(__[Vertex].outTo[Audited].traversal)
      .map {
        case ValueMap(m) ⇒
          val audit = m.get[Vertex]("audit").as[Audit]
          val obj   = m.get[Vertex]("object").asEntity
          audit → obj
      }
      .toList

    val summary = auditList
      .groupBy(_._2._model.label)
      .mapValues(_.groupBy(_._1.operation).mapValues(_.size))
    RichAudit(auditList.head._1, auditList.head._2, summary)
  }

  def getObject: Option[Entity] =
    raw.outTo[Audited].headOption().flatMap { v ⇒
      schema.getModel(v.label()).collect {
        case model: VertexModel ⇒
          model.converter(db, graph).toDomain(v)
      }
    }
}
