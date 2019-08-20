package org.thp.thehive.connector.misp

import akka.actor.PoisonPill
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.thehive.connector.misp.servives.{Connector, MispActor}
import org.thp.thehive.services.{Connector => TheHiveConnector}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

class MispModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[MispRouter]
//    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
//    queryExecutorBindings.addBinding.to[CortexQueryExecutorV0]
    val connectorBindings = ScalaMultibinder.newSetBinder[TheHiveConnector](binder)
    connectorBindings.addBinding.to[Connector]

//    bind(classOf[SchemaUpdater]).asEagerSingleton()
    bindActor[MispActor](
      "misp-actor",
      props =>
        ClusterSingletonManager
          .props(props, PoisonPill, ClusterSingletonManagerSettings(configuration.get[Configuration]("akka.cluster.singleton").underlying))
    )
    ()
  }
}
