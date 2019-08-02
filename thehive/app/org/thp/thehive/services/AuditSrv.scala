package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.structure.Transaction.Status
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Entity, _}
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.util.{Success, Try}

case class PendingAudit(audit: Audit, context: Entity, `object`: Option[Entity])

@Singleton
class AuditSrv @Inject()(
    userSrv: UserSrv,
    eventSrv: EventSrv
)(implicit db: Database, schema: TheHiveSchema)
    extends VertexSrv[Audit, AuditSteps] { auditSrv =>
  val auditUserSrv                                        = new EdgeSrv[AuditUser, Audit, User]
  val auditedSrv                                          = new EdgeSrv[Audited, Audit, Product]
  val auditContextSrv                                     = new EdgeSrv[AuditContext, Audit, Product]
  private var pendingAudit: Map[AnyRef, PendingAudit]     = Map.empty
  private val pendingAuditsLock                           = new Object
  private var transactionAuditIds: List[(AnyRef, String)] = Nil
  private val transactionAuditIdsLock                     = new Object
  private var unauditedTransactions: Set[AnyRef]          = Set.empty
  private val unauditedTransactionsLock                   = new Object

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AuditSteps = new AuditSteps(raw)

  def mergeAudits[R](body: => Try[R])(auditCreator: R => Try[Unit])(implicit graph: Graph): Try[R] = {
    val tx = db.currentTransactionId(graph)
    unauditedTransactionsLock.synchronized {
      unauditedTransactions = unauditedTransactions + tx
    }
    val result = body.flatMap { r =>
      auditCreator(r).map(_ => r)
    }
    unauditedTransactionsLock.synchronized {
      unauditedTransactions = unauditedTransactions - tx
    }
    result
  }

  def create(audit: Audit, context: Entity, `object`: Option[Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {

    def createLastPending(tx: AnyRef)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
      val p = pendingAudit(tx)
      pendingAuditsLock.synchronized {
        pendingAudit = pendingAudit - tx
      }
      createFromPending(tx, p.audit.copy(mainAction = true), p.context, p.`object`).map { _ =>
        val (ids, otherTxIds) = transactionAuditIds.partition(_._1 == tx)
        transactionAuditIdsLock.synchronized {
          transactionAuditIds = otherTxIds
        }
        graph.tx.addTransactionListener {
          case Status.COMMIT => eventSrv.publish(AuditStreamMessage(ids.map(_._2): _*))
          case _             =>
        }
      }
    }

    def createFromPending(tx: AnyRef, audit: Audit, context: Entity, `object`: Option[Entity])(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      for {
        user         <- userSrv.current.getOrFail()
        createdAudit <- create(audit)
        _            <- auditUserSrv.create(AuditUser(), createdAudit, user)
        _            <- auditContextSrv.create(AuditContext(), createdAudit, context)
        _            <- `object`.map(auditedSrv.create(Audited(), createdAudit, _)).flip
      } yield transactionAuditIdsLock.synchronized {
        transactionAuditIds = (tx -> createdAudit._id) :: transactionAuditIds
      }

    def setupCallbacks(tx: AnyRef): Try[Unit] = {
      graph.tx.addTransactionListener {
        case Status.ROLLBACK =>
          pendingAuditsLock.synchronized {
            pendingAudit = pendingAudit - tx
          }
          transactionAuditIdsLock.synchronized {
            transactionAuditIds = transactionAuditIds.filterNot(_._1 == tx)
          }
        case _ =>
      }
      db.addCallback(() => createLastPending(tx))
      Success(())
    }

    val tx = db.currentTransactionId(graph)
    if (unauditedTransactions.contains(tx))
      Success(())
    else {
      val p = pendingAudit.get(tx)
      pendingAuditsLock.synchronized {
        pendingAudit = pendingAudit + (tx -> PendingAudit(audit, context, `object`))
      }
      p.fold(setupCallbacks(tx))(p => createFromPending(tx, p.audit, p.context, p.`object`))
    }
  }

  def get(auditIds: Seq[String])(implicit graph: Graph): AuditSteps = initSteps.filter(_.hasId(auditIds: _*)) // FIXME:ID

  class ObjectAudit[E <: Product, C <: Product] {

    def create(entity: E with Entity, context: C with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit("create", entity), context, Some(entity))

    def update(entity: E with Entity, context: C with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit("update", entity, Some(details.toString)), context, Some(entity))

    def delete(entity: E with Entity, context: C with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = Success(())
    // FIXME there is no context
//      auditSrv.create(Audit("update", entity), context, Some(entity))
  }

  class SelfContextObjectAudit[E <: Product] {

    def create(entity: E with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit("create", entity), entity, Some(entity))

    def update(entity: E with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit("update", entity, Some(details.toString)), entity, Some(entity))

    def delete(entity: E with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = Success(())
    // FIXME there is no context
//      auditSrv.create(Audit("update", entity, None), entity, Some(entity))
  }

  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Entity] =
    get(audit).getObject

  val `case`            = new SelfContextObjectAudit[Case]
  val task              = new ObjectAudit[Task, Case]
  val taskInTemplate    = new ObjectAudit[Task, CaseTemplate]
  val observable        = new ObjectAudit[Observable, Case]
  val observableInAlert = new ObjectAudit[Observable, Alert]
  val log               = new ObjectAudit[Log, Case]
  val alert             = new SelfContextObjectAudit[Alert]
  val user              = new SelfContextObjectAudit[User]
  val caseTemplate      = new SelfContextObjectAudit[CaseTemplate]
  val dashboard         = new SelfContextObjectAudit[Dashboard]
}

@EntitySteps[Audit]
class AuditSteps(raw: GremlinScala[Vertex])(implicit db: Database, schema: Schema, graph: Graph) extends BaseVertexSteps[Audit, AuditSteps](raw) {

  def richAudit: ScalarSteps[RichAudit] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[AuditContext]))
            .and(By(__[Vertex].outTo[Audited].fold()))
        )
        .map {
          case (audit, context, obj) =>
            RichAudit(audit.as[Audit], context.asEntity, atMostOneOf[Vertex](obj).map(_.asEntity))
        }
    )

  def richAuditWithCustomRenderer[A](
      entityRenderer: GremlinScala[Vertex] => GremlinScala[A]
  ): ScalarSteps[(RichAudit, A)] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[AuditContext]))
            .and(By(__[Vertex].outTo[Audited].fold()))
            .and(By(entityRenderer(__[Vertex])))
        )
        .map {
          case (audit, context, obj, renderedObject) =>
            RichAudit(audit.as[Audit], context.asEntity, atMostOneOf[Vertex](obj).map(_.asEntity)) -> renderedObject
        }
    )

  def forCase(caseId: String): AuditSteps =
    newInstance(
      raw.filter(
        _.outTo[AuditContext]
          .in()
          .hasLabel("Share")
          .outTo[ShareCase]
          .hasId(caseId)
      )
    )

  override def newInstance(raw: GremlinScala[Vertex]): AuditSteps = new AuditSteps(raw)

  def visible(implicit authContext: AuthContext): AuditSteps = newInstance(
    raw.filter(
      _.outTo[AuditContext]
        .coalesce(                                   // find organisation
          _.inTo[ShareCase].inTo[OrganisationShare], // case
          _.inTo[ShareTask].inTo[OrganisationShare], // task
          _.inTo[ShareObservable].inTo[OrganisationShare],
          _.outTo[AlertOrganisation]
        )
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has(Key("login") of authContext.userId)
    )
  )

  def getObject: Option[Entity] =
    raw.outTo[Audited].headOption().flatMap { v =>
      schema.getModel(v.label()).collect {
        case model: VertexModel =>
          model.converter(db, graph).toDomain(v)
      }
    }
}
