package org.thp.thehive.services

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.inject.name.Names
import com.google.inject.{Injector, Key => GuiceKey}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.GuiceAkkaExtension
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import play.api.cache.SyncCacheApi

object FlowActor {
  case class FlowId(organisation: String, caseId: Option[String]) {
    override def toString: String = s"$organisation;${caseId.getOrElse("-")}"
  }
  case class AuditIds(ids: Seq[String])
}

class FlowActor extends Actor {
  import FlowActor._

  lazy val injector: Injector  = GuiceAkkaExtension(context.system).injector
  lazy val cache: SyncCacheApi = injector.getInstance(classOf[SyncCacheApi])
  lazy val auditSrv: AuditSrv  = injector.getInstance(classOf[AuditSrv])
  lazy val caseSrv: CaseSrv    = injector.getInstance(classOf[CaseSrv])
  lazy val db: Database        = injector.getInstance(GuiceKey.get(classOf[Database], Names.named("with-thehive-schema")))
  lazy val eventSrv: EventSrv  = injector.getInstance(classOf[EventSrv])

  override def preStart(): Unit = eventSrv.subscribe(StreamTopic(), self)
  override def receive: Receive = {
    case flowId @ FlowId(organisation, caseId) =>
      val auditIds = cache.getOrElseUpdate(flowId.toString) {
        db.roTransaction { implicit graph =>
          caseId
            .fold(auditSrv.startTraversal.has("mainAction", true).visible(organisation))(caseSrv.getByIds(_).audits(organisation))
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
          .has("mainAction", true)
          .project(
            _.by(_._id)
              .by(_.organisation.value(_.name).fold)
              .by(_.`case`._id.fold)
          )
          .toIterator
          .foreach {
            case (id, organisations, cases) =>
              organisations.foreach { organisation =>
                val cacheKey = FlowId(organisation, None).toString
                val ids      = cache.get[List[String]](cacheKey).getOrElse(Nil)
                cache.set(cacheKey, id :: ids)
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
