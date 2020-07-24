package org.thp.thehive.services

import java.util.{Date, List => JList}

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.inject.name.Names
import com.google.inject.{Injector, Key => GuiceKey}
import gremlin.scala.{By, Key}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.GuiceAkkaExtension
import play.api.cache.SyncCacheApi

import scala.collection.JavaConverters._

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
            .fold(auditSrv.initSteps.has("mainAction", true).visible(organisation))(caseSrv.getByIds(_).audits(organisation))
            .order(List(By(Key[Date]("_createdAt"), Order.desc)))
            .range(0, 10)
            ._id
            .toList
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
              .by(_.organisation.name.fold)
              .by(_.`case`._id.fold)
          )
          .toIterator
          .foreach {
            case (id: AnyRef, organisations: JList[String], cases: JList[AnyRef]) =>
              organisations.asScala.foreach { organisation =>
                val cacheKey = FlowId(organisation, None).toString
                val ids      = cache.get[List[String]](cacheKey).getOrElse(Nil)
                cache.set(cacheKey, id.toString :: ids)
                cases.asScala.foreach { caseId =>
                  val cacheKey: String = FlowId(organisation, Some(caseId.toString)).toString
                  val ids              = cache.get[List[String]](cacheKey).getOrElse(Nil)
                  cache.set(cacheKey, (id.toString :: ids).take(10))
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
