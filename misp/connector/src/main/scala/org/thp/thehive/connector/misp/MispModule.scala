package org.thp.thehive.connector.misp

import akka.actor.{ActorRef, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.thehive.connector.misp.services.{Connector, MispActor, MispActorProvider}
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

class MispModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger: Logger = Logger(getClass)

  override def configure(): Unit = {
    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[MispRouter]
    val connectorBindings = ScalaMultibinder.newSetBinder[TheHiveConnector](binder)
    connectorBindings.addBinding.to[Connector]

    bindActor[MispActor](
      "misp-actor-singleton",
      props =>
        ClusterSingletonManager
          .props(
            singletonProps = props,
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(configuration.get[Configuration]("akka.cluster.singleton").underlying)
          )
    )
    bind[ActorRef]
      .annotatedWithName("misp-actor")
      .toProvider[MispActorProvider]
      .asEagerSingleton()
  }
}
