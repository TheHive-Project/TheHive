package org.thp.thehive.services

import java.util.Date

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.inject.name.Names
import com.google.inject.{Injector, Key => GuiceKey}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import org.thp.thehive.GuiceAkkaExtension
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import play.api.cache.SyncCacheApi

import scala.concurrent.duration.FiniteDuration

object FlowActor {
  case class FlowId(organisation: EntityIdOrName, caseId: Option[EntityIdOrName]) {
    override def toString: String = s"$organisation;${caseId.getOrElse("-")}"
  }
  case class AuditIds(ids: Seq[EntityId])
}

class FlowActor extends Actor {
  import FlowActor._

  lazy val injector: Injector           = GuiceAkkaExtension(context.system).injector
  lazy val cache: SyncCacheApi          = injector.getInstance(classOf[SyncCacheApi])
  lazy val auditSrv: AuditSrv           = injector.getInstance(classOf[AuditSrv])
  lazy val caseSrv: CaseSrv             = injector.getInstance(classOf[CaseSrv])
  lazy val db: Database                 = injector.getInstance(GuiceKey.get(classOf[Database], Names.named("with-thehive-schema")))
  lazy val appConfig: ApplicationConfig = injector.getInstance(classOf[ApplicationConfig])
  lazy val maxAgeConfig: ConfigItem[FiniteDuration, FiniteDuration] =
    appConfig.item[FiniteDuration]("flow.maxAge", "Max age of audit logs shown in initial flow")
  def fromDate: Date = new Date(System.currentTimeMillis() - maxAgeConfig.get.toMillis)

  lazy val eventSrv: EventSrv   = injector.getInstance(classOf[EventSrv])
  override def preStart(): Unit = eventSrv.subscribe(StreamTopic(), self)
  override def receive: Receive = {
    case flowId @ FlowId(organisation, caseId) =>
      val auditIds = cache.getOrElseUpdate(flowId.toString) {
        db.roTransaction { implicit graph =>
          caseId
            .fold(auditSrv.startTraversal.has(_.mainAction, true).has(_._createdAt, P.gt(fromDate)).visible(organisation))(
              caseSrv.get(_).audits(organisation)
            )
            .sort(_.by("_createdAt", Order.desc))
            .range(0, 10)
            ._id
            .toSeq
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
              .by(_.organisation._id.fold)
              .by(_.`case`._id.fold)
          )
          .toIterator
          .foreach {
            case (id, organisations, cases) =>
              organisations.foreach { organisation =>
                val cacheKey = FlowId(organisation, None).toString
                val ids      = cache.get[List[String]](cacheKey).getOrElse(Nil)
                cache.set(cacheKey, (id :: ids).take(10))
                cases.foreach { caseId =>
                  val cacheKey: String = FlowId(organisation, Some(caseId)).toString
                  val ids              = cache.get[List[String]](cacheKey).getOrElse(Nil)
                  cache.set(cacheKey, (id :: ids).take(10))
                }
              }
          }
      }
    case _ =>
  }
}

@Singleton
class FlowActorProvider @Inject() (system: ActorSystem) extends Provider[ActorRef] {
  override lazy val get: ActorRef = {
    val singletonManager =
      system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props[FlowActor],
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ),
        name = "flowSingletonManager"
      )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singletonManager.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      ),
      name = "flowSingletonProxy"
    )
  }
}
