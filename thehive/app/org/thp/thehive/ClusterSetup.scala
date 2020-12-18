package org.thp.thehive

import akka.actor.{Actor, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, _}
import com.google.inject.Injector
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Singleton}

@Singleton
class ClusterSetup @Inject() (
    configuration: Configuration,
    system: ActorSystem,
    injector: Injector
) {
  system.actorOf(Props[ClusterListener])
  if (configuration.get[Seq[String]]("akka.cluster.seed-nodes").isEmpty) {
    val logger: Logger = Logger(getClass)
    logger.info("Initialising cluster")
    val cluster = Cluster(system)
    cluster.join(cluster.system.provider.getDefaultAddress)
  }
  GuiceAkkaExtension(system).set(injector)

}

class ClusterListener extends Actor {
  val cluster: Cluster = Cluster(context.system)
  val logger: Logger   = Logger(getClass)

  override def preStart(): Unit = cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member)                      => logger.info(s"Member is Up: ${member.address}")
    case UnreachableMember(member)             => logger.info(s"Member detected as unreachable: $member")
    case MemberRemoved(member, previousStatus) => logger.info(s"Member is Removed: ${member.address} after $previousStatus")
    case MemberJoined(member)                  => logger.debug(s"Member is joined: $member")
    case MemberWeaklyUp(member)                => logger.debug(s"Member is weaklyUp: $member")
    case MemberLeft(member)                    => logger.debug(s"Member is left: $member")
    case MemberExited(member)                  => logger.debug(s"Member is exited: $member")
    case MemberDowned(member)                  => logger.debug(s"Member is downed: $member")
  }
}
