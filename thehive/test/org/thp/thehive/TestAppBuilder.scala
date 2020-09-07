package org.thp.thehive

import java.io.File
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import com.google.inject.Injector
import javax.inject.{Inject, Provider, Singleton}
import org.apache.commons.io.FileUtils
import org.thp.scalligraph.auth._
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.{GenIntegrityCheckOps, LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{janus, AppBuilder}
import org.thp.thehive.controllers.v0.TheHiveQueryExecutor
import org.thp.thehive.models.TheHiveSchemaDefinition
import org.thp.thehive.services.notification.notifiers.{AppendToFileProvider, EmailerProvider, NotifierProvider}
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.{UserSrv => _, _}

import scala.util.Try

object TestAppBuilderLock

trait TestAppBuilder {

  val databaseName: String = "default"

  def appConfigure: AppBuilder =
    (new AppBuilder)
      .bind[UserSrv, LocalUserSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchemaDefinition]
      .bind[QueryExecutor, TheHiveQueryExecutor]
      .multiBind[AuthSrvProvider](classOf[LocalPasswordAuthProvider], classOf[LocalKeyAuthProvider], classOf[HeaderAuthProvider])
      .multiBind[NotifierProvider](classOf[AppendToFileProvider])
      .multiBind[NotifierProvider](classOf[EmailerProvider])
      .multiBind[TriggerProvider](classOf[LogInMyTaskProvider])
      .multiBind[TriggerProvider](classOf[CaseCreatedProvider])
      .multiBind[TriggerProvider](classOf[TaskAssignedProvider])
      .multiBind[TriggerProvider](classOf[AlertCreatedProvider])
      .bindToProvider[AuthSrv, MultiAuthSrvProvider]
      .multiBind[GenIntegrityCheckOps](
        classOf[ProfileIntegrityCheckOps],
        classOf[OrganisationIntegrityCheckOps],
        classOf[TagIntegrityCheckOps],
        classOf[UserIntegrityCheckOps],
        classOf[ImpactStatusIntegrityCheckOps],
        classOf[ResolutionStatusIntegrityCheckOps],
        classOf[ObservableTypeIntegrityCheckOps],
        classOf[CustomFieldIntegrityCheckOps],
        classOf[CaseTemplateIntegrityCheckOps],
        classOf[DataIntegrityCheckOps],
        classOf[CaseIntegrityCheckOps]
      )
      .bindActor[DummyActor]("config-actor")
      .bindActor[DummyActor]("notification-actor")
      .bindActor[DummyActor]("integrity-check-actor")
      .bindActor[DummyActor]("flow-actor")
      .addConfiguration("auth.providers = [{name:local},{name:key},{name:header, userHeader:user}]")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
      .addConfiguration("play.mailer.mock = yes")
      .addConfiguration("play.mailer.debug = yes")
      .addConfiguration(s"storage.localfs.location = ${System.getProperty("user.dir")}/target/storage")
      .bindEagerly[AkkaGuiceExtensionSetup]

  def testApp[A](body: AppBuilder => A): A = {
    val storageDirectory = Files.createTempDirectory(Paths.get("target"), "janusgraph-test-database").toFile
    TestAppBuilderLock.synchronized {
      if (!Files.exists(Paths.get(s"target/janusgraph-test-database-$databaseName"))) {
        val app = appConfigure
          .addConfiguration(s"""
                               |db {
                               |  provider: janusgraph
                               |  janusgraph {
                               |    storage.backend: berkeleyje
                               |    storage.directory: "target/janusgraph-test-database-$databaseName"
                               |    berkeleyje.freeDisk: 2
                               |  }
                               |}
                               |akka.cluster.jmx.multi-mbeans-in-same-jvm: on
                               |""".stripMargin)
          .bind[Database, janus.JanusDatabase]
          .bindNamedToProvider[Database, BasicDatabaseProvider]("with-thehive-schema")

        app[DatabaseBuilder].build()(app[Database], app[UserSrv].getSystemAuthContext)
        app[Database].close()
      }
      FileUtils.copyDirectory(new File(s"target/janusgraph-test-database-$databaseName"), storageDirectory)
    }
    val app = appConfigure
      .bind[Database, janus.JanusDatabase]
      .bindNamedToProvider[Database, BasicDatabaseProvider]("with-thehive-schema")
      .addConfiguration(s"""
                           |db {
                           |  provider: janusgraph
                           |  janusgraph {
                           |    storage.backend: berkeleyje
                           |    storage.directory: $storageDirectory
                           |    berkeleyje.freeDisk: 2
                           |  }
                           |}
                           |""".stripMargin)

    try body(app)
    finally {
      Try(app[Database].close())
      FileUtils.deleteDirectory(storageDirectory)
    }
  }
}

@Singleton
class BasicDatabaseProvider @Inject() (database: Database) extends Provider[Database] {
  override def get(): Database = database
}

@Singleton
class AkkaGuiceExtensionSetup @Inject() (system: ActorSystem, injector: Injector) {
  GuiceAkkaExtension(system).set(injector)
}
