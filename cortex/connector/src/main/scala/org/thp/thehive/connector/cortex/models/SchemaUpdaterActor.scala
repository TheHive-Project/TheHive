package org.thp.thehive.connector.cortex.models

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import javax.inject.{Inject, Named, Provider, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Try

@Singleton
class DatabaseProvider @Inject() (
    cortexSchema: CortexSchemaDefinition,
    @Named("with-thehive-schema") database: Database,
    actorSystem: ActorSystem
) extends Provider[Database] {
  import SchemaUpdaterActor._
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
    Await.result(schemaUpdaterActor ? RequestDBStatus, timeout.duration) match {
      case DBStatus(status) =>
        status.get
        database
    }
  }
}

object SchemaUpdaterActor {
  case object RequestDBStatus
  case class DBStatus(status: Try[Unit])
}

class SchemaUpdaterActor @Inject() (cortexSchema: CortexSchemaDefinition, database: Database) extends Actor {
  import SchemaUpdaterActor._
  lazy val logger: Logger = Logger(getClass)

  def update(): Try[Unit] =
    cortexSchema
      .update(database)(LocalUserSrv.getSystemAuthContext)
      .recover {
        case error => logger.error(s"Database with CortexSchema schema update failure", error)
      }

  override def receive: Receive = {
    case RequestDBStatus =>
      val status = update()
      sender ! DBStatus(status)
      context.become(receive(status))
  }

  def receive(status: Try[Unit]): Receive = {
    case RequestDBStatus =>
      status.fold({ _ =>
        val newStatus = update()
        sender ! DBStatus(newStatus)
        context.become(receive(newStatus))
      }, _ => sender ! DBStatus(status))
  }
}
