package org.thp.thehive

import _root_.controllers.Assets
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.softwaremill.macwire.wire
import com.softwaremill.tagging.@@
import org.thp.scalligraph.auth.{AuthSrv, AuthSrvFactory, AuthSrvProvider}
import org.thp.scalligraph.models.{Database, UpdatableSchema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigTag}
import org.thp.scalligraph.services.{EventSrv, StorageSrv}
import org.thp.scalligraph.{
  BadRequestError,
  InternalError,
  LazyMutableSeq,
  ScalligraphApplication,
  ScalligraphApplicationImpl,
  ScalligraphModule,
  SingleInstance
}
import play.api.cache.SyncCacheApi
import play.api.http.{FileMimeTypes, HttpConfiguration}
import play.api.libs.Files.{TemporaryFileCreator, TemporaryFileReaper}
import play.api.libs.ws.WSClient
import play.api.mvc.DefaultActionBuilder
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, Configuration, LoggerConfigurator, Mode}

import java.io.File
import scala.concurrent.ExecutionContext
import scala.reflect.{classTag, ClassTag}

object TestApplicationNoDatabase extends ScalligraphApplicationImpl(new File("."), getClass.getClassLoader, Mode.Test) {
  override lazy val database: Database = throw new IllegalStateException("Database from root application should not be used")
}

class TestApplication(override val database: Database) extends ScalligraphApplication {
  override def getQueryExecutor(version: Int): QueryExecutor =
    syncCacheApi.getOrElseUpdate(s"QueryExecutor.$version") {
      queryExecutors()
        .filter(_.versionCheck(version))
        .reduceOption(_ ++ _)
        .getOrElse(throw BadRequestError(s"No available query executor for version $version"))
    }

  override lazy val fileMimeTypes: FileMimeTypes                 = TestApplicationNoDatabase.fileMimeTypes
  override val routers: LazyMutableSeq[Router]                   = LazyMutableSeq[Router]
  override lazy val tempFileCreator: TemporaryFileCreator        = TestApplicationNoDatabase.tempFileCreator
  override lazy val tempFileReaper: TemporaryFileReaper          = TestApplicationNoDatabase.tempFileReaper
  override lazy val singleInstance: SingleInstance               = TestApplicationNoDatabase.singleInstance
  override lazy val defaultActionBuilder: DefaultActionBuilder   = TestApplicationNoDatabase.defaultActionBuilder
  override lazy val wsClient: WSClient                           = TestApplicationNoDatabase.wsClient
  override lazy val materializer: Materializer                   = TestApplicationNoDatabase.materializer
  override lazy val storageSrv: StorageSrv                       = TestApplicationNoDatabase.storageSrv
  override lazy val syncCacheApi: SyncCacheApi                   = TestApplicationNoDatabase.syncCacheApi
  override lazy val actorSystem: ActorSystem                     = TestApplicationNoDatabase.actorSystem
  override lazy val application: Application                     = TestApplicationNoDatabase.application
  override lazy val configuration: Configuration                 = TestApplicationNoDatabase.configuration
  override lazy val context: ApplicationLoader.Context           = TestApplicationNoDatabase.context
  override lazy val httpConfiguration: HttpConfiguration         = TestApplicationNoDatabase.httpConfiguration
  implicit override val executionContext: ExecutionContext       = TestApplicationNoDatabase.executionContext
  override def assets: Assets                                    = TestApplicationNoDatabase.assets
  override val schemas: LazyMutableSeq[UpdatableSchema]          = LazyMutableSeq[UpdatableSchema]
  override val queryExecutors: LazyMutableSeq[QueryExecutor]     = LazyMutableSeq[QueryExecutor]
  override val authSrvProviders: LazyMutableSeq[AuthSrvProvider] = LazyMutableSeq[AuthSrvProvider]
  override lazy val configActor: ActorRef @@ ConfigTag = {
    singleInstance
    TestApplicationNoDatabase.configActor
  }
  override lazy val eventSrv: EventSrv                   = TestApplicationNoDatabase.eventSrv
  override lazy val applicationConfig: ApplicationConfig = wire[ApplicationConfig]
  override lazy val authSrv: AuthSrv                     = AuthSrvFactory(this)

  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }

  private var _loadedModules: Seq[ScalligraphModule] = Seq.empty
  override def getModule[M: ClassTag]: M = {
    val moduleClass = classTag[M].runtimeClass
    loadedModules
      .find(m => moduleClass.isAssignableFrom(m.getClass))
      .getOrElse(throw InternalError(s"The module $moduleClass is not found"))
      .asInstanceOf[M]
  }
  override def injectModule(module: ScalligraphModule): Unit = _loadedModules = _loadedModules :+ module
  override def loadedModules: Seq[ScalligraphModule]         = _loadedModules
}
