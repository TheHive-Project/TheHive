package org.thp.thehive.services

import akka.actor.ActorRef
import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.structure.Transaction.Status
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Entity, _}
import org.thp.scalligraph.services._
import org.thp.thehive.models._
import org.thp.thehive.services.notification.AuditNotificationMessage
import play.api.Logger
import play.api.libs.json.JsObject

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

case class PendingAudit(audit: Audit, context: Option[Entity], `object`: Option[Entity])

@Singleton
class AuditSrv @Inject()(
    userSrvProvider: Provider[UserSrv],
    @Named("notification-actor") notificationActor: ActorRef,
    eventSrv: EventSrv
)(implicit db: Database, schema: TheHiveSchema)
    extends VertexSrv[Audit, AuditSteps] { auditSrv =>
  lazy val logger                                         = Logger(getClass)
  lazy val userSrv: UserSrv                               = userSrvProvider.get
  val auditUserSrv                                        = new EdgeSrv[AuditUser, Audit, User]
  val auditedSrv                                          = new EdgeSrv[Audited, Audit, Product]
  val auditContextSrv                                     = new EdgeSrv[AuditContext, Audit, Product]
  val `case`                                              = new SelfContextObjectAudit[Case]
  val task                                                = new ObjectAudit[Task, Case]
  val observable                                          = new ObjectAudit[Observable, Case]
  val log                                                 = new ObjectAudit[Log, Case]
  val caseTemplate                                        = new SelfContextObjectAudit[CaseTemplate]
  val taskInTemplate                                      = new ObjectAudit[Task, CaseTemplate]
  val alert                                               = new SelfContextObjectAudit[Alert]
  val observableInAlert                                   = new ObjectAudit[Observable, Alert]
  val user                                                = new SelfContextObjectAudit[User]
  val dashboard                                           = new SelfContextObjectAudit[Dashboard]
  val organisation                                        = new SelfContextObjectAudit[Organisation]
  private val pendingAuditsLock                           = new Object
  private val transactionAuditIdsLock                     = new Object
  private val unauditedTransactionsLock                   = new Object
  private var pendingAudits: Map[AnyRef, PendingAudit]    = Map.empty
  private var transactionAuditIds: List[(AnyRef, String)] = Nil
  private var unauditedTransactions: Set[AnyRef]          = Set.empty

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AuditSteps = new AuditSteps(raw)

  def mergeAudits[R](body: => Try[R])(auditCreator: R => Try[Unit])(implicit graph: Graph): Try[R] = {
    val tx = db.currentTransactionId(graph)
    unauditedTransactionsLock.synchronized {
      unauditedTransactions = unauditedTransactions + tx
    }
    val result = body
    unauditedTransactionsLock.synchronized {
      unauditedTransactions = unauditedTransactions - tx
    }
    result.flatMap { r =>
      auditCreator(r).map(_ => r)
    }
  }

  def create(audit: Audit, context: Option[Entity], `object`: Option[Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {

    def createLastPending(tx: AnyRef)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
      logger.debug("Store last audit")
      val p = pendingAudits(tx)
      pendingAuditsLock.synchronized {
        pendingAudits = pendingAudits - tx
      }
      createFromPending(tx, p.audit.copy(mainAction = true), p.context, p.`object`).map { _ =>
        val (ids, otherTxIds) = transactionAuditIds.partition(_._1 == tx)
        transactionAuditIdsLock.synchronized {
          transactionAuditIds = otherTxIds
        }
        db.addTransactionListener {
          case Status.COMMIT =>
            logger.debug("Sending audit to stream bus and to notification actor")
            val auditIds = ids.map(_._2)
            eventSrv.publish(StreamTopic())(AuditStreamMessage(auditIds: _*))
            notificationActor ! AuditNotificationMessage(auditIds: _*)
          case _ =>
        }
      }
    }

    def createFromPending(tx: AnyRef, audit: Audit, context: Option[Entity], `object`: Option[Entity])(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] = {
      logger.debug(s"Store audit entity: $audit")
      for {
        user         <- userSrv.current.getOrFail()
        createdAudit <- create(audit)
        _            <- auditUserSrv.create(AuditUser(), createdAudit, user)
        _            <- `object`.map(auditedSrv.create(Audited(), createdAudit, _)).flip
        _ = context.map(auditContextSrv.create(AuditContext(), createdAudit, _)).flip // this could fail on delete (context doesn't exist)
      } yield transactionAuditIdsLock.synchronized {
        transactionAuditIds = (tx -> createdAudit._id) :: transactionAuditIds
      }
    }

    def setupCallbacks(tx: AnyRef): Try[Unit] = {
      logger.debug("Setup callbacks for the current transaction")
      db.addTransactionListener {
        case Status.ROLLBACK =>
          pendingAuditsLock.synchronized {
            pendingAudits = pendingAudits - tx
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
    if (unauditedTransactions.contains(tx)) {
      logger.debug(s"Audit is disable to the current transaction, $audit ignored.")
      Success(())
    } else {
      logger.debug(s"Hold $audit, store previous audit if any")
      val p = pendingAudits.get(tx)
      pendingAuditsLock.synchronized {
        pendingAudits = pendingAudits + (tx -> PendingAudit(audit, context, `object`))
      }
      p.fold(setupCallbacks(tx))(p => createFromPending(tx, p.audit, p.context, p.`object`))
    }
  }

  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Entity] = get(audit).`object`.headOption()

  class ObjectAudit[E <: Product, C <: Product] {

    def create(entity: E with Entity, context: C with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity), Some(context), Some(entity))

    def update(entity: E with Entity, context: C with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), Some(context), Some(entity))

    def delete(entity: E with Entity, context: Option[C with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, None), context, None)
  }

  class SelfContextObjectAudit[E <: Product] {

    def create(entity: E with Entity, details: Option[JsObject] = None)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity, details.map(_.toString)), Some(entity), Some(entity))

    def update(entity: E with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), Some(entity), Some(entity))

    def delete(entity: E with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, None), None, None)
  }
}

@EntitySteps[Audit]
class AuditSteps(raw: GremlinScala[Vertex])(implicit db: Database, schema: Schema, graph: Graph) extends BaseVertexSteps[Audit, AuditSteps](raw) {

  def organisation: OrganisationSteps =
    new OrganisationSteps(getOrganisation(raw))

  private def getOrganisation(r: GremlinScala[Vertex]): GremlinScala[Vertex] =
    r.outTo[AuditContext]
      .choose[Label, Vertex](
        on = _.label(),
        BranchCase("Case", new CaseSteps(_).organisations.raw),
        BranchCase("CaseTemplate", new TaskSteps(_).`case`.organisations.raw),
        BranchCase("Alert", new AlertSteps(_).organisation.raw),
        BranchCase("User", new UserSteps(_).organisations.raw),
        BranchCase("Dashboard", new DashboardSteps(_).organisation.raw)
        //          BranchOtherwise(_)
      )

  def auditContextOrganisation: ScalarSteps[(Audit with Entity, Option[Entity], Option[Organisation with Entity])] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[AuditContext].fold()))
            .and(By(getOrganisation(__[Vertex]).fold()))
        )
        .map {
          case (audit, context, organisation) =>
            (audit.as[Audit], context.asScala.headOption.map(_.asEntity), organisation.asScala.headOption.map(_.as[Organisation]))
        }
    )

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
      _.outTo[AuditContext]                          // TODO use choose step
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

  def `object`: ScalarSteps[Entity] = ScalarSteps(raw.outTo[Audited].map(_.asEntity))
}
