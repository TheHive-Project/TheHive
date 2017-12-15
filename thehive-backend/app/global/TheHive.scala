package global

import scala.collection.JavaConverters._

import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{ Configuration, Environment, Logger, Mode }

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors.Connector
import controllers.{ AssetCtrl, AssetCtrlDev, AssetCtrlProd }
import models.Migration
import net.codingwell.scalaguice.{ ScalaModule, ScalaMultibinder }
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import services._

import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.auth.MultiAuthSrv
import org.elastic4play.services.{ AuthSrv, MigrationOperations }

class TheHive(
    environment: Environment,
    val configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  private[TheHive] lazy val logger = Logger(s"module")

  override def configure(): Unit = {
    bind[org.elastic4play.services.UserSrv].to[services.UserSrv]
    bind[Int].annotatedWith(Names.named("databaseVersion")).toInstance(models.modelVersion)

    val modelBindings = ScalaMultibinder.newSetBinder[BaseModelDef](binder)
    val auditedModelBindings = ScalaMultibinder.newSetBinder[AuditedModel](binder)
    val authBindings = ScalaMultibinder.newSetBinder[AuthSrv](binder)

    val reflectionClasses = new Reflections(new ConfigurationBuilder()
      .forPackages("org.elastic4play")
      .forPackages("connectors.cortex")
      .forPackages("connectors.misp")
      .forPackages("connectors.metrics")
      .addClassLoader(getClass.getClassLoader)
      .addClassLoader(environment.getClass.getClassLoader)
      .setExpandSuperTypes(false)
      .setScanners(new SubTypesScanner(false)))

    reflectionClasses
      .getSubTypesOf(classOf[BaseModelDef])
      .asScala
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .foreach { modelClass ⇒
        logger.info(s"Loading model $modelClass")
        modelBindings.addBinding.to(modelClass)
        if (classOf[AuditedModel].isAssignableFrom(modelClass)) {
          auditedModelBindings.addBinding.to(modelClass.asInstanceOf[Class[AuditedModel]])
        }
      }

    reflectionClasses
      .getSubTypesOf(classOf[AuthSrv])
      .asScala
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
      .filterNot(c ⇒ c == classOf[MultiAuthSrv] || c == classOf[TheHiveAuthSrv])
      .foreach { authSrvClass ⇒
        authBindings.addBinding.to(authSrvClass)
      }

    bind[MigrationOperations].to[Migration]
    bind[AuthSrv].to[TheHiveAuthSrv]

    bindActor[AuditActor]("AuditActor")
    bindActor[DeadLetterMonitoringActor]("DeadLetterMonitoringActor")

    if (environment.mode == Mode.Prod)
      bind[AssetCtrl].to[AssetCtrlProd]
    else
      bind[AssetCtrl].to[AssetCtrlDev]

    ScalaMultibinder.newSetBinder[Connector](binder)
    ()
  }
}