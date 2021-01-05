package org.thp.thehive.connector.cortex.models

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import javax.inject.{Inject, Named, Provider, Singleton}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@Singleton
class DatabaseProvider @Inject() (
    cortexSchema: CortexSchemaDefinition,
    @Named("with-thehive-schema") database: Database,
    actorSystem: ActorSystem
) extends Provider[Database] {
  lazy val schemaUpdaterActor: ActorRef = {
    val singletonManager =
      actorSystem.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props(classOf[SchemaUpdaterActor], cortexSchema, database),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(actorSystem)
        ),
        name = "theHiveCortexSchemaUpdaterSingletonManager"
      )

    actorSystem.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singletonManager.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(actorSystem)
      ),
      name = "theHiveCortexSchemaUpdaterSingletonProxy"
    )
  }

  override def get(): Database = {
    implicit val timeout: Timeout = Timeout(5.minutes)
    Await.result(schemaUpdaterActor ? RequestDB, timeout.duration) match {
      case DBReady => database
    }
  }
}

sealed trait SchemaUpdaterMessage
case object RequestDB extends SchemaUpdaterMessage
case object DBReady   extends SchemaUpdaterMessage

class SchemaUpdaterActor @Inject() (cortexSchema: CortexSchemaDefinition, database: Database) extends Actor {
  lazy val logger: Logger = Logger(getClass)

  def update(): Unit = {
    cortexSchema
      .update(database)(LocalUserSrv.getSystemAuthContext)
      .recover {
        case error => logger.error(s"Database with CortexSchema schema update failure", error)
      }
    ()
  }

  override def receive: Receive = {
    case RequestDB =>
      update()
      sender ! DBReady
      context.become(databaseUpToDate)
  }

  def databaseUpToDate: Receive = {
    case RequestDB =>
      sender ! DBReady
  }
}
