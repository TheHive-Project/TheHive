package org.thp.thehive.services

import akka.actor.Actor
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import org.thp.thehive.models.{Audit, AuditContext}
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TaskOps._
import play.api.cache.SyncCacheApi

import java.util.Date
import scala.concurrent.duration.FiniteDuration

sealed trait FlowMessage
case class FlowId(caseId: Option[EntityIdOrName])(implicit val authContext: AuthContext) extends FlowMessage {
  def organisationId: Option[EntityId] = authContext.organisation.fold(Some(_), _ => None)
}
object FlowId {
  def toString(organisationId: EntityId, caseId: Option[EntityIdOrName]): String =
    s"$organisationId;${caseId.getOrElse("-")}"
}
case class AuditIds(ids: Seq[EntityId]) extends FlowMessage

sealed trait FlowTag
class FlowActor(
    cache: SyncCacheApi,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv,
    taskSrv: TaskSrv,
    db: Database,
    appConfig: ApplicationConfig,
    eventSrv: EventSrv,
    auditSrv: AuditSrv
) extends Actor {

  lazy val maxAgeConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("flow.maxAge", "Max age of audit logs shown in initial flow")
  def fromDate: Date = new Date(System.currentTimeMillis() - maxAgeConfig.get.toMillis)

  override def preStart(): Unit = eventSrv.subscribe(StreamTopic(), self)
  override def postStop(): Unit = eventSrv.unsubscribe(StreamTopic(), self)

  def flowQuery(
      caseId: Option[EntityIdOrName]
  )(implicit graph: Graph, authContext: AuthContext): Traversal[EntityId, AnyRef, Converter[EntityId, AnyRef]] =
    caseId match {
      case None =>
        auditSrv
          .startTraversal
          .has(_.mainAction, true)
          .has(_._createdAt, P.gt(fromDate))
          .sort(_.by("_createdAt", Order.desc))
          .visible(organisationSrv)
          .limit(10)
          ._id
      case Some(cid) =>
        graph
          .union(
            caseSrv.filterTraversal(_).get(cid).visible(organisationSrv).in[AuditContext],
            observableSrv.filterTraversal(_).visible(organisationSrv).relatedTo(caseSrv.caseId(cid)).in[AuditContext],
            taskSrv.filterTraversal(_).visible(organisationSrv).relatedTo(caseSrv.caseId(cid)).in[AuditContext]
          )
          .v[Audit]
          .has(_.mainAction, true)
          .sort(_.by("_createdAt", Order.desc))
          .limit(10)
          ._id

    }

  override def receive: Receive = {
    case flowId: FlowId =>
      val organisationId = flowId.organisationId.getOrElse {
        db.roTransaction { implicit graph =>
          organisationSrv.currentId(graph, flowId.authContext)
        }
      }
      val auditIds = cache.getOrElseUpdate(FlowId.toString(organisationId, flowId.caseId)) {
        db.roTransaction { implicit graph =>
          flowQuery(flowId.caseId)(graph, flowId.authContext).toSeq
        }
      }
      sender ! AuditIds(auditIds)
    case AuditStreamMessage(ids @ _*) =>
      db.roTransaction { implicit graph =>
        auditSrv
          .getByIds(ids: _*)
          .has(_.mainAction, true)
          .project(
            _.by(_._id)
              .by(_.organisationIds.dedup().fold)
              .by(_.caseId.fold)
          )
          .toIterator
          .foreach {
            case (id, organisations, cases) =>
              organisations.foreach { organisation =>
                val cacheKey = FlowId.toString(organisation, None)
                val ids      = cache.get[List[String]](cacheKey).getOrElse(Nil)
                cache.set(cacheKey, (id :: ids).take(10))
                cases.foreach { caseId =>
                  val cacheKey: String = FlowId.toString(organisation, Some(caseId))
                  val ids              = cache.get[List[String]](cacheKey).getOrElse(Nil)
                  cache.set(cacheKey, (id :: ids).take(10))
                }
              }
          }
      }
    case _ =>
  }
}
