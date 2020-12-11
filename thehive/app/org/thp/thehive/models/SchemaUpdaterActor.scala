package org.thp.thehive.models

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Database
import org.thp.thehive.ClusterSetup
import org.thp.thehive.services.LocalUserSrv
import play.api.{Configuration, Logger}

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}

@Singleton
class DatabaseProvider @Inject() (
    configuration: Configuration,
    database: Database,
    theHiveSchema: TheHiveSchemaDefinition,
    actorSystem: ActorSystem,
    clusterSetup: ClusterSetup // this dependency is here to ensure that cluster setup is finished
) extends Provider[Database] {

  lazy val dbInitialisationTimeout: FiniteDuration = configuration.get[FiniteDuration]("db.initialisationTimeout")
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

  def databaseInstance: String =
    database match {
      case jdb: JanusDatabase => jdb.instanceId
      case _                  => ""
    }

  override def get(): Database = {
    implicit val timeout: Timeout = Timeout(dbInitialisationTimeout)
    Await.result(schemaUpdaterActor ? RequestDB(databaseInstance), timeout.duration) match {
      case DBReady => database.asInstanceOf[Database]
    }
  }
}

sealed trait SchemaUpdaterMessage
case class RequestDB(databaseInstanceId: String) extends SchemaUpdaterMessage
case object DBReady                              extends SchemaUpdaterMessage

class SchemaUpdaterActor @Inject() (theHiveSchema: TheHiveSchemaDefinition, database: Database) extends Actor {

  lazy val logger: Logger = Logger(getClass)

  final case object Update

  implicit val ec: ExecutionContext      = context.dispatcher
  var originalConnectionIds: Set[String] = Set.empty

  def update(): Unit = {
    theHiveSchema
      .update(database)(LocalUserSrv.getSystemAuthContext)
      .map(_ => logger.info("Database is up-to-date"))
      .recover {
        case error => logger.error(s"Database with TheHiveSchema schema update failure", error)
      }
    logger.info("Install eventual missing indexes")
    database.addSchemaIndexes(theHiveSchema).recover {
      case error => logger.error(s"Database with TheHiveSchema index update failure", error)
    }
    ()
  }

  override def preStart(): Unit = {
    originalConnectionIds = database match {
      case jdb: JanusDatabase => jdb.openInstances
      case _                  => Set.empty
    }
    logger.debug(s"Database open instances are: ${originalConnectionIds.mkString(",")}")
  }

  def hasUnknownConnections(instanceIds: Set[String]): Boolean = (originalConnectionIds -- instanceIds).nonEmpty

  def dropUnknownConnections(instanceIds: Set[String]): Unit =
    database match {
      case jdb: JanusDatabase => jdb.dropConnections((originalConnectionIds -- instanceIds).toSeq)
      case _                  =>
    }

  override def receive: Receive = {
    case RequestDB(instanceId) =>
      val instanceIds = Set(instanceId)
      if (hasUnknownConnections(instanceIds)) {
        logger.info("Database has unknown connections, wait 5 seconds for full cluster initialisation")
        context.system.scheduler.scheduleOnce(5.seconds, self, Update)
        context.become(receive(instanceIds, Seq(sender)))
      } else {
        logger.info("Database is ready to be updated")
        update()
        sender ! DBReady
        context.become(receive(instanceIds, Nil))
      }

      def receive(instanceIds: Set[String], waitingClients: Seq[ActorRef]): Receive = {
        case RequestDB(instanceId) if waitingClients.nonEmpty =>
          context.become(receive(instanceIds + instanceId, waitingClients :+ sender))
        case RequestDB(_) =>
          sender ! DBReady
        case Update =>
          logger.info("Drop unknown connections and update the database")
          dropUnknownConnections(instanceIds)
          update()
          waitingClients.foreach(_ ! DBReady)
          context.become(receive(instanceIds, Nil))
      }
  }
}
