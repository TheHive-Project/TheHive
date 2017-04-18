package global

import java.net.{ URL, URLClassLoader }

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors.Connector
import controllers.{ AssetCtrl, AssetCtrlDev, AssetCtrlProd }
import models.Migration
import net.codingwell.scalaguice.{ ScalaModule, ScalaMultibinder }
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.auth.MultiAuthSrv
import org.elastic4play.services.{ AuthSrv, AuthSrvFactory, MigrationOperations, TempFilter }
import org.reflections.Reflections
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.mvc.EssentialFilter
import play.api.{ Configuration, Environment, Logger, Mode }
import services.{ AuditSrv, AuditedModel, StreamFilter, StreamMonitor }

import scala.collection.JavaConversions.asScalaSet

class TheHive(
    environment: Environment,
    val configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  private[TheHive] lazy val logger = Logger(s"module")

  override def configure(): Unit = {
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
        logger.info(s"Loading model $modelClass")
        modelBindings.addBinding.to(modelClass)
        if (classOf[AuditedModel].isAssignableFrom(modelClass)) {
          auditedModelBindings.addBinding.to(modelClass.asInstanceOf[Class[AuditedModel]])
        }
      }

    new Reflections(new org.reflections.util.ConfigurationBuilder()
      .addUrls(packageUrls: _*)
      .setScanners(new org.reflections.scanners.SubTypesScanner(false)))
      .getSubTypesOf(classOf[AuthSrv])
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
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

    val filterBindings = ScalaMultibinder.newSetBinder[EssentialFilter](binder)
    filterBindings.addBinding.to[StreamFilter]
    filterBindings.addBinding.to[TempFilter]
    filterBindings.addBinding.to[CSRFFilter]

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