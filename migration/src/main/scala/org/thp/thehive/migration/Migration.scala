package org.thp.thehive.migration

import java.io.File

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.cache.ehcache.EhCacheModule
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}
import play.api.{Configuration, Environment, Logger}

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.google.inject.name.Names
import com.typesafe.config.ConfigFactory
import javax.inject.{Inject, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.thp.scalligraph.auth.{UserSrv ⇒ UserDB}
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Schema, SchemaChecker}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models._
import org.thp.thehive.services._

import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.{DatabaseState, MigrationOperations, Operation}

@Singleton
class Migration @Inject()(
    config: Configuration,
    organisationSrv: OrganisationSrv,
    schema: TheHiveSchema,
    implicit val db: Database,
    userDB: UserDB,
    userMigration: UserMigration,
    dbListMigration: DBListMigration,
    caseTemplateMigration: CaseTemplateMigration,
    caseMigration: CaseMigration,
    alertMigration: AlertMigration,
    system: ActorSystem) {

  lazy val logger = Logger(getClass)

  def migrate(): Unit =
    userMigration.withUser("init") { implicit authContext ⇒
      // create default organisation
      val organisationName = config.get[String]("organisation.name")
      val defaultOrganisation = Try(db.transaction(implicit graph ⇒ organisationSrv.create(Organisation(organisationName))))
        .orElse(db.transaction(implicit graph ⇒ organisationSrv.getOrFail(organisationName)))
        .get
      logger.info(s"organisation $organisationName created")

      Terminal { terminal ⇒
        userMigration.importUsers(terminal, defaultOrganisation)
        dbListMigration.importDBLists(terminal)
        caseTemplateMigration.importCaseTemplates(terminal, defaultOrganisation)
        caseMigration.importCases(terminal, defaultOrganisation)
        alertMigration.importAlerts(terminal, defaultOrganisation)
      }
    }
}

class DummyMigrationOperations extends MigrationOperations {
  override val operations: PartialFunction[DatabaseState, Seq[Operation]] = PartialFunction.empty
  override def beginMigration(version: Int): Future[Unit]                 = Future.successful(())
  override def endMigration(version: Int): Future[Unit]                   = Future.successful(())
}

class MigrationModule(configuration: Configuration) extends ScalaModule {
  override def configure(): Unit = {
    val system = ActorSystem("TheHiveMigration")
    bind[ActorSystem].toInstance(system)
    bind[ExecutionContext].toInstance(system.dispatcher)
    bind[Materializer].toInstance(ActorMaterializer()(system))

    bind[immutable.Set[BaseModelDef]].toInstance(Set.empty)
    bind[Int].annotatedWithName("databaseVersion").toInstance(14)
    bind[MigrationOperations].to[DummyMigrationOperations]
    bind[UserDB].to[LocalUserSrv]
    bind[Database].to[JanusDatabase]
//    bind[Database].to[OrientDatabase]
//    bind[Database].to[RemoteJanusDatabase]
    bind[StorageSrv].to[LocalFileSystemStorageSrv]
//    bind[StorageSrv].to[HadoopStorageSrv]
    bind[Configuration].toInstance(configuration)
    bind[Environment].toInstance(Environment.simple())
    bind[ApplicationLifecycle].to[DefaultApplicationLifecycle]
    bind[Schema].to[TheHiveSchema]
    bind[Int].annotatedWith(Names.named("schemaVersion")).toInstance(1)
    bind[SchemaChecker].asEagerSingleton()
    ()
  }
}

object Start extends App {
  val config = new Configuration(ConfigFactory.parseFileAnySyntax(new File("conf/migration.conf"))) // TODO read filename from argument
//  (new LogbackLoggerConfigurator).configure(Environment.simple(), Configuration.empty, Map.empty)
  new GuiceApplicationBuilder()
    .loadConfig(config)
    .load(new MigrationModule(config), new EhCacheModule)
    .injector()
    .instanceOf[Migration]
    .migrate()
  System.exit(0)
}
