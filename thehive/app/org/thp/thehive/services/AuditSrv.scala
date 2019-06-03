package org.thp.thehive.services
import java.util.Date

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.util.Try

@Singleton
class AuditSrv @Inject()(
    userSrv: UserSrv,
    eventSrv: EventSrv
)(implicit db: Database, schema: Schema)
    extends VertexSrv[Audit, AuditSteps] {
  val auditUserSrv    = new EdgeSrv[AuditUser, Audit, User]
  val auditedSrv      = new EdgeSrv[Audited, Audit, Product]
  val auditContextSrv = new EdgeSrv[AuditContext, Audit, Product]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AuditSteps = new AuditSteps(raw)

  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Entity] =
    get(audit).getObject

  //  def create(audit: Audit, context: Entity, `object`: Option[Entity]): Try[RichAudit] = ???

  def create(
      audit: Audit,
      context: Entity,
      `object`: Option[Entity]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichAudit] =
    userSrv.current.getOrFail().map { user ⇒
      val createdAudit = create(audit)
      auditUserSrv.create(AuditUser(), createdAudit, user)
      auditContextSrv.create(AuditContext(), createdAudit, context)
      `object`.map(auditedSrv.create(Audited(), createdAudit, _))
      val richAudit = RichAudit(createdAudit, context, `object`)
      db.onSuccessTransaction(graph)(() ⇒ eventSrv.publish(richAudit))
      richAudit
    }

  def createCase(`case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[RichAudit] =
    create(Audit(authContext.requestId, "createCase", Some(`case`._id), Some("Case"), None), `case`, Some(`case`))

  def updateCase(`case`: Case with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[RichAudit] =
    create(Audit(authContext.requestId, "updateCase", Some(`case`._id), Some("Case"), Some(details.toString)), `case`, Some(`case`))

  def createAlert(alert: Alert with Entity)(implicit graph: Graph, authContext: AuthContext): Try[RichAudit] =
    create(Audit(authContext.requestId, "createCase", Some(alert._id), Some("Alert"), None), alert, Some(alert))

  def updateAlert(alert: Alert with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[RichAudit] =
    create(Audit(authContext.requestId, "updateAlert", Some(alert._id), Some("Alert"), Some(details.toString)), alert, Some(alert))

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
      .mapValues(_.groupBy(_._1.action).mapValues(_.size))
//    RichAudit(auditList.head._1, auditList.head._2, summary)
    ???
  }

  def getObject: Option[Entity] =
    raw.outTo[Audited].headOption().flatMap { v ⇒
      schema.getModel(v.label()).collect {
        case model: VertexModel ⇒
          model.converter(db, graph).toDomain(v)
      }
    }
}
