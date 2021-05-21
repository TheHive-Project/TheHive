package org.thp.thehive

import _root_.controllers.Assets
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.softwaremill.tagging.@@
import org.thp.scalligraph.auth.{AuthSrv, AuthSrvProvider}
import org.thp.scalligraph.models.{Database, UpdatableSchema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigTag}
import org.thp.scalligraph.services.{EventSrv, StorageSrv}
import org.thp.scalligraph.{BadRequestError, LazyMutableSeq, ScalligraphApplication, ScalligraphModule, SingleInstance}
import play.api.cache.SyncCacheApi
import play.api.http.{FileMimeTypes, HttpConfiguration}
import play.api.libs.Files.{TemporaryFileCreator, TemporaryFileReaper}
import play.api.libs.ws.WSClient
import play.api.mvc.DefaultActionBuilder
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, Configuration, LoggerConfigurator}

import scala.concurrent.ExecutionContext
import scala.reflect.{classTag, ClassTag}

class TestApplication(override val database: Database, testApplicationNoDatabase: ScalligraphApplication) extends ScalligraphApplication {
  override def getQueryExecutor(version: Int): QueryExecutor =
    syncCacheApi.getOrElseUpdate(s"QueryExecutor.$version") {
      queryExecutors()
        .filter(_.versionCheck(version))
        .reduceOption(_ ++ _)
        .getOrElse(throw BadRequestError(s"No available query executor for version $version"))
    }

  override lazy val fileMimeTypes: FileMimeTypes                 = testApplicationNoDatabase.fileMimeTypes
  override val routers: LazyMutableSeq[Router]                   = LazyMutableSeq[Router]
  override lazy val tempFileCreator: TemporaryFileCreator        = testApplicationNoDatabase.tempFileCreator
  override lazy val tempFileReaper: TemporaryFileReaper          = testApplicationNoDatabase.tempFileReaper
  override lazy val singleInstance: SingleInstance               = testApplicationNoDatabase.singleInstance
  override lazy val defaultActionBuilder: DefaultActionBuilder   = testApplicationNoDatabase.defaultActionBuilder
  override lazy val wsClient: WSClient                           = testApplicationNoDatabase.wsClient
  override lazy val materializer: Materializer                   = testApplicationNoDatabase.materializer
  override lazy val storageSrv: StorageSrv                       = testApplicationNoDatabase.storageSrv
  override lazy val syncCacheApi: SyncCacheApi                   = testApplicationNoDatabase.syncCacheApi
  override lazy val actorSystem: ActorSystem                     = testApplicationNoDatabase.actorSystem
  override lazy val application: Application                     = testApplicationNoDatabase.application
  override lazy val configuration: Configuration                 = testApplicationNoDatabase.configuration
  override lazy val context: ApplicationLoader.Context           = testApplicationNoDatabase.context
  override lazy val httpConfiguration: HttpConfiguration         = testApplicationNoDatabase.httpConfiguration
  implicit override val executionContext: ExecutionContext       = testApplicationNoDatabase.executionContext
  override def assets: Assets                                    = testApplicationNoDatabase.assets
  override val schemas: LazyMutableSeq[UpdatableSchema]          = LazyMutableSeq[UpdatableSchema]
  override val queryExecutors: LazyMutableSeq[QueryExecutor]     = LazyMutableSeq[QueryExecutor]
  override val authSrvProviders: LazyMutableSeq[AuthSrvProvider] = LazyMutableSeq[AuthSrvProvider]
  override def configActor: ActorRef @@ ConfigTag                = testApplicationNoDatabase.configActor
  override def eventSrv: EventSrv                                = testApplicationNoDatabase.eventSrv
  override def applicationConfig: ApplicationConfig              = testApplicationNoDatabase.applicationConfig
  override def authSrv: AuthSrv                                  = testApplicationNoDatabase.authSrv

  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }

  private var _loadedModules: Seq[ScalligraphModule] = Seq.empty
  override def getModule[M: ClassTag]: M = {
    val moduleClass = classTag[M].runtimeClass
    loadedModules.find(m => moduleClass.isAssignableFrom(m.getClass)).getOrElse(???).asInstanceOf[M]
  }
  override def injectModule(module: ScalligraphModule): Unit = _loadedModules = _loadedModules :+ module
  override def loadedModules: Seq[ScalligraphModule]         = _loadedModules
}
