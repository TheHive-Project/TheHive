package org.thp.thehive.services

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.google.inject.util.Types
import com.google.inject.{Injector, Key, TypeLiteral}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.services.{GenIntegrityCheckOps, IntegrityCheckOps}
import org.thp.thehive.GuiceAkkaExtension
import play.api.{Configuration, Logger}

import java.util.{Set => JSet}
import javax.inject.{Inject, Provider, Singleton}
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Success

sealed trait IntegrityCheckMessage
case class EntityAdded(name: String) extends IntegrityCheckMessage
case class NeedCheck(name: String)   extends IntegrityCheckMessage
case class Check(name: String)       extends IntegrityCheckMessage

class IntegrityCheckActor() extends Actor {

  lazy val logger: Logger               = Logger(getClass)
  lazy val injector: Injector           = GuiceAkkaExtension(context.system).injector
  lazy val configuration: Configuration = injector.getInstance(classOf[Configuration])
  lazy val integrityCheckOps: immutable.Set[IntegrityCheckOps[_ <: Product]] = injector
    .getInstance(Key.get(TypeLiteral.get(Types.setOf(classOf[GenIntegrityCheckOps]))))
    .asInstanceOf[JSet[IntegrityCheckOps[_ <: Product]]]
    .asScala
    .toSet
  lazy val db: Database                       = injector.getInstance(classOf[Database])
  lazy val schema: Schema                     = injector.getInstance(classOf[Schema])
  lazy val defaultInitalDelay: FiniteDuration = configuration.get[FiniteDuration]("integrityCheck.default.initialDelay")
  def initialDelay(name: String): FiniteDuration =
    configuration.getOptional[FiniteDuration](s"integrityCheck.$name.initialDelay").getOrElse(defaultInitalDelay)
  lazy val defaultInterval: FiniteDuration = configuration.get[FiniteDuration]("integrityCheck.default.interval")
  def interval(name: String): FiniteDuration =
    configuration.getOptional[FiniteDuration](s"integrityCheck.$name.interval").getOrElse(defaultInitalDelay)

  lazy val integrityCheckMap: Map[String, IntegrityCheckOps[_]] =
    integrityCheckOps.map(d => d.name -> d).toMap
  def check(name: String): Unit = integrityCheckMap.get(name).foreach(_.check())

  override def preStart(): Unit = {
    implicit val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
    integrityCheckOps.foreach { integrityCheck =>
      db.tryTransaction { implicit graph =>
        Success(integrityCheck.initialCheck())
      }
    }
    integrityCheckOps.foreach { integrityCheck =>
      Success(integrityCheck.check())
    }
  }
  override def receive: Receive = receive(Map.empty)
  def receive(states: Map[String, (Boolean, Cancellable)]): Receive = {
    case EntityAdded(name) =>
      logger.debug(s"An entity $name has been created")
      context.system.scheduler.scheduleOnce(initialDelay(name), self, NeedCheck(name))(context.system.dispatcher)
      ()
    case NeedCheck(name) if !states.contains(name) => // initial check
      logger.debug(s"Initial integrity check of $name")
      check(name)
      val timer = context.system.scheduler.scheduleAtFixedRate(Duration.Zero, interval(name), self, Check(name))(context.system.dispatcher)
      context.become(receive(states + (name -> (false -> timer))))
    case NeedCheck(name) =>
      if (!states(name)._1) {
        val timer = states(name)._2
        context.become(receive(states + (name -> (true -> timer))))
      }
    case Check(name) if states.get(name).fold(false)(_._1) => // stats.needCheck == true
      logger.debug(s"Integrity check of $name")
      check(name)
      val timer = states(name)._2
      context.become(receive(states + (name -> (false -> timer))))
    case Check(name) =>
      logger.debug(s"Pause integrity checks of $name, wait new add")
      states.get(name).foreach(_._2.cancel())
      context.become(receive(states - name))
  }
}

@Singleton
class IntegrityCheckActorProvider @Inject() (system: ActorSystem) extends Provider[ActorRef] {
  override lazy val get: ActorRef = {
    val singletonManager =
      system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props[IntegrityCheckActor],
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ),
        name = "integrityCheckSingletonManager"
      )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singletonManager.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      ),
      name = "integrityCheckSingletonProxy"
    )
  }
}
