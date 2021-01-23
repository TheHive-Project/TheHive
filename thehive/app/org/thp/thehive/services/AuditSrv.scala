package org.thp.thehive.services

import akka.actor.ActorRef
import com.google.inject.name.Named
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Transaction.Status
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Entity, _}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, IdentityConverter, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.DashboardOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.notification.AuditNotificationMessage
import play.api.libs.json.{JsObject, JsValue, Json}

import java.util.{Map => JMap}
import javax.inject.{Inject, Provider, Singleton}
import scala.util.{Success, Try}

case class PendingAudit(audit: Audit, context: Product with Entity, `object`: Option[Product with Entity])

@Singleton
class AuditSrv @Inject() (
    userSrvProvider: Provider[UserSrv],
    @Named("notification-actor") notificationActor: ActorRef,
    eventSrv: EventSrv,
    db: Database
) extends VertexSrv[Audit] { auditSrv =>
  lazy val userSrv: UserSrv                                 = userSrvProvider.get
  val alert                                                 = new SelfContextObjectAudit[Alert]
  val alertToCase                                           = new ObjectAudit[Alert, Case]
  val auditedSrv                                            = new EdgeSrv[Audited, Audit, Product]
  val auditContextSrv                                       = new EdgeSrv[AuditContext, Audit, Product]
  val auditUserSrv                                          = new EdgeSrv[AuditUser, Audit, User]
  val `case`                                                = new SelfContextObjectAudit[Case]
  val caseTemplate                                          = new SelfContextObjectAudit[CaseTemplate]
  val customField                                           = new SelfContextObjectAudit[CustomField]
  val dashboard                                             = new SelfContextObjectAudit[Dashboard]
  val log                                                   = new ObjectAudit[Log, Task]
  val observable                                            = new SelfContextObjectAudit[Observable]
  val observableInAlert                                     = new ObjectAudit[Observable, Alert]
  val organisation                                          = new SelfContextObjectAudit[Organisation]
  val page                                                  = new SelfContextObjectAudit[Page]
  val pattern                                               = new SelfContextObjectAudit[Pattern]
  val procedure                                             = new ObjectAudit[Procedure, Case]
  val profile                                               = new SelfContextObjectAudit[Profile]
  val share                                                 = new ShareAudit
  val task                                                  = new SelfContextObjectAudit[Task]
  val taskInTemplate                                        = new ObjectAudit[Task, CaseTemplate]
  val user                                                  = new UserAudit
  private val pendingAuditsLock                             = new Object
  private val transactionAuditIdsLock                       = new Object
  private val unauditedTransactionsLock                     = new Object
  private var pendingAudits: Map[AnyRef, PendingAudit]      = Map.empty
  private var transactionAuditIds: List[(AnyRef, EntityId)] = Nil
  private var unauditedTransactions: Set[AnyRef]            = Set.empty

  /**
    * Gets the main action Audits by ids sorted by date
    * @param order the sort
    * @param ids the ids
    * @param graph db
    * @return
    */
  def getMainByIds(order: Order, ids: EntityId*)(implicit graph: Graph): Traversal.V[Audit] =
    getByIds(ids: _*)
      .has(_.mainAction, true)
      .sort(_.by("_createdAt", order))

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

  private def createFromPending(tx: AnyRef, audit: Audit, context: Product with Entity, `object`: Option[Product with Entity])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
    logger.debug(s"Store audit entity: $audit")
    for {
      user         <- userSrv.current.getOrFail("User")
      createdAudit <- createEntity(audit)
      _            <- auditUserSrv.create(AuditUser(), createdAudit, user)
      _            <- `object`.map(auditedSrv.create(Audited(), createdAudit, _)).flip
      _ = auditContextSrv.create(AuditContext(), createdAudit, context) // this could fail on delete (context doesn't exist)
    } yield transactionAuditIdsLock.synchronized {
      transactionAuditIds = (tx -> createdAudit._id) :: transactionAuditIds
    }
  }

  def create(audit: Audit, context: Product with Entity, `object`: Option[Product with Entity])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] = {
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

  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Product with Entity] = get(audit).`object`.entity.headOption

  class ObjectAudit[E <: Product, C <: Product] {

    def create(entity: E with Entity, context: C with Entity, details: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity, Some(details.toString)), context, Some(entity))

    def update(entity: E with Entity, context: C with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      if (details == JsObject.empty) Success(())
      else auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), context, Some(entity))

    def delete(entity: E with Entity, context: C with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, None), context, None)

    def merge(entity: E with Entity, destination: C with Entity, details: Option[JsObject] = None)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(Audit(Audit.merge, destination, details.map(_.toString())), destination, Some(destination))
  }

  class SelfContextObjectAudit[E <: Product] {

    def create(entity: E with Entity, details: JsValue)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(Audit(Audit.create, entity, Some(details.toString)), entity, Some(entity))

    def update(entity: E with Entity, details: JsObject)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      if (details == JsObject.empty) Success(())
      else auditSrv.create(Audit(Audit.update, entity, Some(details.toString)), entity, Some(entity))

    def delete(entity: E with Entity, context: Product with Entity, details: Option[JsObject] = None)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(Audit(Audit.delete, entity, details.map(_.toString())), context, None)
  }

  class UserAudit extends SelfContextObjectAudit[User] {

    def changeProfile(user: User with Entity, organisation: Organisation with Entity, profile: Profile)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, user, Some(Json.obj("organisation" -> organisation.name, "profile" -> profile.name).toString)),
        organisation,
        Some(user)
      )

    def delete(user: User with Entity, organisation: Organisation with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.delete, user, Some(Json.obj("organisation" -> organisation.name).toString)),
        organisation,
        None
      )
  }

  class ShareAudit {

    def shareCase(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, `case`, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name, "profile" -> profile.name)).toString)),
        `case`,
        Some(`case`)
      )

    def shareTask(task: Task with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, task, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name)).toString)),
        task,
        Some(`case`)
      )

    def shareObservable(observable: Observable with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, observable, Some(Json.obj("share" -> Json.obj("organisation" -> organisation.name)).toString)),
        observable,
        Some(`case`)
      )

    def unshareCase(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, `case`, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        `case`,
        Some(`case`)
      )

    def unshareTask(task: Task with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, task, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        task,
        Some(`case`)
      )

    def unshareObservable(observable: Observable with Entity, `case`: Case with Entity, organisation: Organisation with Entity)(implicit
        graph: Graph,
        authContext: AuthContext
    ): Try[Unit] =
      auditSrv.create(
        Audit(Audit.update, observable, Some(Json.obj("unshare" -> Json.obj("organisation" -> organisation.name)).toString)),
        observable,
        Some(`case`)
      )
  }
}

object AuditOps {

  implicit class VertexDefs(traversal: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]) {
    def share: Traversal.V[Share] = traversal.coalesceIdent(_.in[ShareObservable], _.in[ShareTask], _.in[ShareCase]).v[Share]
  }

  implicit class AuditOpsDefs(traversal: Traversal.V[Audit]) {
    def auditContextObjectOrganisation
        : Traversal[(Audit with Entity, Option[Entity], Option[Entity], Option[Organisation with Entity]), JMap[String, Any], Converter[
          (Audit with Entity, Option[Entity], Option[Entity], Option[Organisation with Entity]),
          JMap[String, Any]
        ]] =
      traversal
        .project(
          _.by
            .by(_.context.entity.fold)
            .by(_.`object`.entity.fold)
            .by(_.organisation.v[Organisation].fold)
        )
        .domainMap {
          case (audit, context, obj, organisation) => (audit, context.headOption, obj.headOption, organisation.headOption)
        }

    def richAudit: Traversal[RichAudit, JMap[String, Any], Converter[RichAudit, JMap[String, Any]]] =
      traversal
        .filter(_.context)
        .project(
          _.by
            .by(_.`case`.entity.fold)
            .by(_.context.entity)
            .by(_.`object`.entity.fold)
        )
        .domainMap {
          case (audit, context, visibilityContext, obj) =>
            val ctx = if (context.isEmpty) visibilityContext else context.head
            RichAudit(audit, ctx, visibilityContext, obj.headOption)
        }

    def richAuditWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Audit] => Traversal[D, G, C]
    ): Traversal[(RichAudit, D), JMap[String, Any], Converter[(RichAudit, D), JMap[String, Any]]] =
      traversal
        .filter(_.context)
        .project(
          _.by
            .by(_.`case`.entity.fold)
            .by(_.context.entity.fold)
            .by(_.`object`.entity.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (audit, context, visibilityContext, obj, renderedObject) =>
            val ctx = if (context.isEmpty) visibilityContext.head else context.head
            RichAudit(audit, ctx, visibilityContext.head, obj.headOption) -> renderedObject
        }

//    def forCase(caseId: String): Traversal.V[Audit] = traversal.filter(_.`case`.hasId(caseId))

    def `case`: Traversal.V[Case] =
      traversal
        .out[AuditContext]
        .share
        .out[ShareCase]
        .v[Case]

    def organisation: Traversal.V[Organisation] =
      traversal
        .out[AuditContext]
        .coalesceIdent[Vertex](
          _.share.in[OrganisationShare],
          _.out[AlertOrganisation],
          _.hasLabel("Organisation"),
          _.out[CaseTemplateOrganisation],
          _.in[OrganisationDashboard]
        )
        .v[Organisation]

    def organisationIds: Traversal[EntityId, JMap[String, Any], Converter[EntityId, JMap[String, Any]]] =
      traversal
        .out[AuditContext]
        .choose(
          _.on(_.label)
            .option("Case", _.v[Case].value(_.organisationIds))
            .option("Observable", _.v[Observable].value(_.organisationIds))
            .option("Task", _.v[Task].value(_.organisationIds))
            .option("Alert", _.v[Alert].value(_.organisationId))
            .option("Organisation", _.v[Organisation]._id)
            .option("CaseTemplate", _.v[CaseTemplate].organisation._id)
            .option("Dashboard", _.v[Dashboard].organisation._id)
        )

    def caseId: Traversal[EntityId, JMap[String, Any], Converter[EntityId, JMap[String, Any]]] =
      traversal
        .out[AuditContext]
        .choose(
          _.on(_.label)
            .option("Case", _.v[Case]._id)
            .option("Observable", _.v[Observable].value(_.relatedId))
            .option("Task", _.v[Task].value(_.relatedId))
        )
    def visible(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Audit] =
      traversal.filter(
        _.out[AuditContext].choose(
          _.on(_.label)
            .option("Case", _.v[Case].visible(organisationSrv))
            .option("Observable", _.v[Observable].visible(organisationSrv))
            .option("Task", _.v[Task].visible(organisationSrv))
            .option("Alert", _.v[Alert].visible(organisationSrv))
            .option("Organisation", _.v[Organisation].current)
            .option("CaseTemplate", _.v[CaseTemplate].visible)
            .option("Dashboard", _.v[Dashboard].visible)
        )
      )

    def `object`: Traversal[Vertex, Vertex, IdentityConverter[Vertex]] = traversal.out[Audited]

    def context: Traversal[Vertex, Vertex, IdentityConverter[Vertex]] = traversal.out[AuditContext]
  }

}
