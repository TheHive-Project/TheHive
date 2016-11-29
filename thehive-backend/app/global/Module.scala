package global

import java.net.{ URL, URLClassLoader }

import javax.inject.Singleton

import scala.collection.JavaConversions.asScalaSet

import play.api.{ Configuration, Environment, Logger, Mode }
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.mvc.Filter

import org.elastic4play.Timed
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.{ AuthSrv, AuthSrvFactory, MigrationOperations, TempFilter }
import org.elastic4play.services.auth.MultiAuthSrv
import org.reflections.Reflections

import net.codingwell.scalaguice.{ ScalaModule, ScalaMultibinder }

import com.google.inject.AbstractModule
import com.google.inject.name.Names

import connectors.Connector
import controllers.{ AssetCtrl, AssetCtrlDev, AssetCtrlProd }
import models.Migration
import services.{ AuditSrv, AuditedModel, StreamFilter, StreamMonitor }

class TheHive(
    environment: Environment,
    val configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  val log = Logger(s"module")

  def configure = {
    bind[org.elastic4play.services.UserSrv].to[services.UserSrv]
    bind[Int].annotatedWith(Names.named("databaseVersion")).toInstance(models.version)

    val modelBindings = ScalaMultibinder.newSetBinder[BaseModelDef](binder)
    val auditedModelBindings = ScalaMultibinder.newSetBinder[AuditedModel](binder)
    val authBindings = ScalaMultibinder.newSetBinder[AuthSrv](binder)
    val authFactoryBindings = ScalaMultibinder.newSetBinder[AuthSrvFactory](binder)

    val packageUrls = Seq(getClass.getClassLoader, classOf[org.elastic4play.Timed].getClassLoader).flatMap {
      case ucl: URLClassLoader ⇒ ucl.getURLs
      case _                   ⇒ Array.empty[URL]
    }

    new Reflections(new org.reflections.util.ConfigurationBuilder()
      .addUrls(packageUrls: _*)
      .setScanners(new org.reflections.scanners.SubTypesScanner(false)))
      .getSubTypesOf(classOf[BaseModelDef])
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .foreach { modelClass ⇒
        log.info(s"Loading model $modelClass")
        modelBindings.addBinding.to(modelClass)
        if (classOf[AuditedModel].isAssignableFrom(modelClass)) {
          auditedModelBindings.addBinding.to(modelClass.asInstanceOf[Class[AuditedModel]])
        }
      }

    new Reflections(new org.reflections.util.ConfigurationBuilder()
      .addUrls(packageUrls: _*)
      .setScanners(new org.reflections.scanners.SubTypesScanner(false)))
      .getSubTypesOf(classOf[AuthSrv])
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers) || c.isMemberClass())
      .filterNot(_ == classOf[MultiAuthSrv])
      .foreach { modelClass ⇒
        authBindings.addBinding.to(modelClass)
      }

    new Reflections(new org.reflections.util.ConfigurationBuilder()
      .addUrls(packageUrls: _*)
      .setScanners(new org.reflections.scanners.SubTypesScanner(false)))
      .getSubTypesOf(classOf[AuthSrvFactory])
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .foreach { modelClass ⇒
        authFactoryBindings.addBinding.to(modelClass)
      }

    val filterBindings = ScalaMultibinder.newSetBinder[Filter](binder)
    filterBindings.addBinding.to[StreamFilter]
    filterBindings.addBinding.to[TempFilter]

    bind[MigrationOperations].to[Migration]
    bind[AuthSrv].to[MultiAuthSrv]
    bind[StreamMonitor].asEagerSingleton()
    bind[AuditSrv].asEagerSingleton()

    if (environment.mode == Mode.Prod)
      bind[AssetCtrl].to[AssetCtrlProd]
    else
      bind[AssetCtrl].to[AssetCtrlDev]

    ScalaMultibinder.newSetBinder[Connector](binder)
    ()
  }
}