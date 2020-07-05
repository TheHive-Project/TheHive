package org.thp.thehive.services

import java.util.Date

import akka.actor.ActorRef
import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Transaction.Status
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Entity, _}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, TraversalLike, VertexSteps}
import org.thp.thehive.models._
import org.thp.thehive.services.notification.AuditNotificationMessage
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

case class PendingAudit(audit: Audit, context: Option[Entity], `object`: Option[Entity])

@Singleton
class AuditSrv @Inject() (
    userSrvProvider: Provider[UserSrv],
    @Named("notification-actor") notificationActor: ActorRef,
    eventSrv: EventSrv
)(implicit @Named("with-thehive-schema") db: Database)
    extends VertexSrv[Audit, AuditSteps] { auditSrv =>
  lazy val userSrv: UserSrv                               = userSrvProvider.get
  val auditUserSrv                                        = new EdgeSrv[AuditUser, Audit, User]
  val auditedSrv                                          = new EdgeSrv[Audited, Audit, Product]
  val auditContextSrv                                     = new EdgeSrv[AuditContext, Audit, Product]
  val `case`                                              = new SelfContextObjectAudit[Case]
  val task                                                = new SelfContextObjectAudit[Task]
  val observable                                          = new SelfContextObjectAudit[Observable]
  val log                                                 = new ObjectAudit[Log, Task]
  val caseTemplate                                        = new SelfContextObjectAudit[CaseTemplate]
  val taskInTemplate                                      = new ObjectAudit[Task, CaseTemplate]
  val alert                                               = new SelfContextObjectAudit[Alert]
  val alertToCase                                         = new ObjectAudit[Alert, Case]
  val share                                               = new ShareAudit
  val observableInAlert                                   = new ObjectAudit[Observable, Alert]
  val user                                                = new UserAudit
  val dashboard                                           = new SelfContextObjectAudit[Dashboard]
  val organisation                                        = new SelfContextObjectAudit[Organisation]
  val profile                                             = new SelfContextObjectAudit[Profile]
  val customField                                         = new SelfContextObjectAudit[CustomField]
  val page                                                = new SelfContextObjectAudit[Page]
  private val pendingAuditsLock                           = new Object
  private val transactionAuditIdsLock                     = new Object
  private val unauditedTransactionsLock                   = new Object
  private var pendingAudits: Map[AnyRef, PendingAudit]    = Map.empty
  private var transactionAuditIds: List[(AnyRef, String)] = Nil
  private var unauditedTransactions: Set[AnyRef]          = Set.empty

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AuditSteps = new AuditSteps(raw)

  /**
    * Gets the main action Audits by ids sorted by date
    * @param order the sort
    * @param ids the ids
    * @param graph db
    * @return
    */
  def getMainByIds(order: Order, ids: String*)(implicit graph: Graph): AuditSteps =
    getByIds(ids: _*)
      .has("mainAction", true)
      .order(List(By(Key[Date]("_createdAt"), order)))

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

  def flushPendingAudit()(implicit graph: Graph, authContext: AuthContext): Try[Unit] = flushPendingAudit(db.currentTransactionId(graph))

  def flushPendingAudit(tx: AnyRef)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    logger.debug("Store last audit")
    pendingAudits.get(tx).fold[Try[Unit]](Success(())) { p =>
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
  }

  private def createFromPending(tx: AnyRef, audit: Audit, context: Option[Entity], `object`: Option[Entity])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    logger.debug(s"Store audit entity: $audit")
    for {
      user         <- userSrv.current.getOrFail()
      createdAudit <- createEntity(audit)
      _            <- auditUserSrv.create(AuditUser(), createdAudit, user)
      _            <- `object`.map(auditedSrv.create(Audited(), createdAudit, _)).flip
      _ = context.map(auditContextSrv.create(AuditContext(), createdAudit, _)).flip // this could fail on delete (context doesn't exist)
    } yield transactionAuditIdsLock.synchronized {
      transactionAuditIds = (tx -> createdAudit._id) :: transactionAuditIds
    }
  }

  def create(audit: Audit, context: Option[Entity], `object`: Option[Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
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
      db.addCallback(() => flushPendingAudit(tx))
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

    def create(entity: E with Entity, context: C with Entity, details: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity, Some(details.toString)), Some(context), Some(entity))

    def update(entity: E with Entity, context: C with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      if (details == JsObject.empty) Success(())
      else auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), Some(context), Some(entity))

    def delete(entity: E with Entity, context: Option[C with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, None), context, None)

    def merge(entity: E with Entity, destination: C with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.merge, entity), Some(destination), None)
  }

  class SelfContextObjectAudit[E <: Product] {

    def create(entity: E with Entity, details: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity, Some(details.toString)), Some(entity), Some(entity))

    def update(entity: E with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      if (details == JsObject.empty) Success(())
      else auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), Some(entity), Some(entity))

    def delete(entity: E with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, None), None, None)
  }

  class UserAudit extends SelfContextObjectAudit[User] {

    def changeProfile(user: User with Entity, organisation: Organisation, profile: Profile)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, user, Some(Json.obj("organisation" -> organisation.name, "profile" -> profile.name).toString)),
        Some(user),
        Some(user)
      )

    def delete(user: User with Entity, organisation: Organisation with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.delete, user, Some(Json.obj("organisation" -> organisation.name).toString)),
        None,
        None
      )
  }

  class ShareAudit {

    def shareCase(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, `case`, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name, "profile" -> profile.name)).toString)),
        Some(`case`),
        Some(`case`)
      )

    def shareTask(task: Task with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, task, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name)).toString)),
        Some(task),
        Some(`case`)
      )

    def shareObservable(observable: Observable with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, observable, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name)).toString)),
        Some(observable),
        Some(`case`)
      )

    def unshareCase(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, `case`, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        Some(`case`),
        Some(`case`)
      )

    def unshareTask(task: Task with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, task, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        Some(task),
        Some(`case`)
      )

    def unshareObservable(observable: Observable with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(
        implicit graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, observable, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        Some(observable),
        Some(`case`)
      )
  }
}

@EntitySteps[Audit]
class AuditSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Audit](raw) {

  def auditContextObjectOrganisation(implicit schema: Schema): Traversal[
    (Audit with Entity, Option[Entity], Option[Entity], Option[Organisation with Entity]),
    (Audit with Entity, Option[Entity], Option[Entity], Option[Organisation with Entity])
  ] =
    this
      .project(
        _.by
          .by(_.context.fold)
          .by(_.`object`.fold)
          .by(_.organisation.fold)
      )
      .map {
        case (audit, context, obj, organisation) =>
          (
            audit.as[Audit],
            context.asScala.headOption.map(_.asEntity),
            obj.asScala.headOption.map(_.asEntity),
            organisation.asScala.headOption.map(_.as[Organisation])
          )
      }

  def richAudit(implicit schema: Schema): Traversal[RichAudit, RichAudit] =
    this
      .project(
        _.by
          .by(_.`case`.fold)
          .by(_.context)
          .by(_.`object`.fold)
      )
      .map {
        case (audit, context, visibilityContext, obj) =>
          val ctx = if (context.isEmpty) visibilityContext else context.get(0)
          RichAudit(audit.as[Audit], ctx.asEntity, visibilityContext.asEntity, atMostOneOf[Vertex](obj).map(_.asEntity))

      }

  def richAuditWithCustomRenderer[A](
      entityRenderer: AuditSteps => TraversalLike[_, A]
  )(implicit schema: Schema): Traversal[(RichAudit, A), (RichAudit, A)] =
    this
      .project(
        _.by
          .by(_.`case`.fold)
          .by(_.context)
          .by(_.`object`.fold)
          .by(entityRenderer)
      )
      .map {
        case (audit, context, visibilityContext, obj, renderedObject) =>
          val ctx = if (context.isEmpty) visibilityContext else context.get(0)
          RichAudit(audit.as[Audit], ctx.asEntity, visibilityContext.asEntity, atMostOneOf[Vertex](obj).map(_.asEntity)) -> renderedObject
      }

  def forCase(caseId: String): AuditSteps = this.filter(_.`case`.hasId(caseId))

  def `case`: CaseSteps =
    new CaseSteps(
      raw
        .outTo[AuditContext]
        .in()
        .hasLabel("Share")
        .outTo[ShareCase]
    )

  def organisation: OrganisationSteps = new OrganisationSteps(
    raw
      .outTo[AuditContext]
      .coalesce(
        _.hasLabel("Organisation"),
        _.in().hasLabel("Share").inTo[OrganisationShare],
        _.both().hasLabel("Organisation")
      )
  )
  override def newInstance(newRaw: GremlinScala[Vertex]): AuditSteps = new AuditSteps(newRaw)

  def visible(implicit authContext: AuthContext): AuditSteps = visible(authContext.organisation)

  def visible(organisationName: String): AuditSteps = this.filter(_.organisation.has("name", organisationName))

  override def newInstance(): AuditSteps = new AuditSteps(raw.clone())

  def `object`: VertexSteps[_ <: Product] = new VertexSteps[Entity](raw.outTo[Audited])

  def context: VertexSteps[_ <: Product] = new VertexSteps[Entity](raw.outTo[AuditContext])
//    Traversal(raw.outTo[AuditContext].map(_.asEntity))
}
