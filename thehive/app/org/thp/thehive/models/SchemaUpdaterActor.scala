package org.thp.thehive.models

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import javax.inject.{Inject, Provider, Singleton}
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Database
import org.thp.thehive.ClusterSetup
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Try}

@Singleton
class DatabaseProvider @Inject() (
    database: Database,
    theHiveSchema: TheHiveSchemaDefinition,
    actorSystem: ActorSystem,
    clusterSetup: ClusterSetup
) extends Provider[Database] {
  import SchemaUpdaterActor._
  lazy val schemaUpdaterActor: ActorRef = {
    val singletonManager =
      actorSystem.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props(classOf[SchemaUpdaterActor], theHiveSchema, database),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(actorSystem)
        ),
        name = "theHiveSchemaUpdaterSingletonManager"
      )

    actorSystem.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singletonManager.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(actorSystem)
      ),
      name = "theHiveSchemaUpdaterSingletonProxy"
    )
  }

  def databaseInstance: String = database match {
    case jdb: JanusDatabase => jdb.instanceId
    case _                  => ""
  }

  override def get(): Database = {
    implicit val timeout: Timeout = Timeout(5.minutes)
    Await.result(schemaUpdaterActor ? RequestDBStatus(databaseInstance), timeout.duration) match {
      case DBStatus(status) =>
        status.get
        database.asInstanceOf[Database]
    }
  }
}

object SchemaUpdaterActor {
  case class RequestDBStatus(databaseInstanceId: String)
  case class DBStatus(status: Try[Unit])
}

class SchemaUpdaterActor @Inject() (theHiveSchema: TheHiveSchemaDefinition, database: Database) extends Actor {
  import SchemaUpdaterActor._
  lazy val logger: Logger = Logger(getClass)
  final case object Update
  implicit val ec: ExecutionContext      = context.dispatcher
  var originalConnectionIds: Set[String] = Set.empty

  def update(): Try[Unit] = {
    theHiveSchema
      .update(database)(LocalUserSrv.getSystemAuthContext)
      .map(_ => logger.info("Database is up-to-date"))
      .recover {
        case error => logger.error(s"Database with TheHiveSchema schema update failure", error)
      }
    logger.info("Install eventual missing indexes")
    database.addSchemaIndexes(theHiveSchema)
  }

  override def preStart(): Unit = {
    originalConnectionIds = database match {
      case jdb: JanusDatabase => jdb.openInstances
      case _                  => Set.empty
    }
    logger.debug(s"Database open instances are: ${originalConnectionIds.mkString(",")}")
  }

  def hasUnknownConnections(instanceIds: Set[String]): Boolean = (originalConnectionIds -- instanceIds).nonEmpty
  def dropUnknownConnections(instanceIds: Set[String]): Unit = database match {
    case jdb: JanusDatabase => jdb.dropConnections((originalConnectionIds -- instanceIds).toSeq)
    case _                  =>
  }

  override def receive: Receive = {
    case RequestDBStatus(instanceId) =>
      val instanceIds = Set(instanceId)
      if (hasUnknownConnections(instanceIds)) {
        logger.info("Database has unknown connections, wait 5 seconds for full cluster initialisation")
        context.system.scheduler.scheduleOnce(5.seconds, self, Update)
        context.become(receive(Failure(new Exception("Update delayed")), instanceIds, Seq(sender)))
      } else {
        logger.info("Database is ready to be updated")
        val status = update()
        sender ! DBStatus(status)
        context.become(receive(status, instanceIds, Nil))
      }
  }

  def receive(status: Try[Unit], instanceIds: Set[String], waitingClients: Seq[ActorRef]): Receive = {
    case RequestDBStatus(instanceId) if waitingClients.nonEmpty =>
      context.become(receive(status, instanceIds + instanceId, waitingClients :+ sender))
    case RequestDBStatus(_) =>
      status.fold(
        { _ =>
          logger.info("Retry to update database")
          val newStatus = update()
          sender ! DBStatus(newStatus)
          context.become(receive(newStatus, instanceIds, waitingClients))
        },
        _ => sender ! DBStatus(status)
      )
    case Update =>
      logger.info("Drop unknown connections and update the database")
      dropUnknownConnections(instanceIds)
      val newStatus = update()
      waitingClients.foreach(_ ! DBStatus(newStatus))
      context.become(receive(newStatus, instanceIds, Nil))
  }
}
