package global

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors.Connector
import controllers.{AssetCtrl, AssetCtrlDev, AssetCtrlProd}
import models.Migration
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.auth.MultiAuthSrv
import org.elastic4play.services.{AuthSrv, MigrationOperations, UserSrv => EUserSrv}
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment, Logger, Mode}
import services._
import services.mappers.{MultiUserMapperSrv, UserMapper}

import java.lang.reflect.Modifier
import scala.collection.JavaConverters._

class TheHive(environment: Environment, val configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  private[TheHive] lazy val logger = Logger(s"module")

  override def configure(): Unit = {
    bind[EUserSrv].to[services.UserSrv]
    bind[Int].annotatedWith(Names.named("databaseVersion")).toInstance(models.modelVersion)

    val modelBindings        = ScalaMultibinder.newSetBinder[BaseModelDef](binder)
    val auditedModelBindings = ScalaMultibinder.newSetBinder[AuditedModel](binder)
    val authBindings         = ScalaMultibinder.newSetBinder[AuthSrv](binder)
    val ssoMapperBindings    = ScalaMultibinder.newSetBinder[UserMapper](binder)

    val reflectionClasses = new Reflections(
      new ConfigurationBuilder()
        .forPackages("org.elastic4play")
        .forPackages("connectors.cortex")
        .forPackages("connectors.misp")
        .forPackages("connectors.metrics")
        .addClassLoaders(getClass.getClassLoader)
        .addClassLoaders(environment.getClass.getClassLoader)
        .setExpandSuperTypes(false)
        .setScanners(Scanners.SubTypes)
    )

    reflectionClasses
      .getSubTypesOf(classOf[BaseModelDef])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .foreach { modelClass =>
        logger.info(s"Loading model $modelClass")
        modelBindings.addBinding.to(modelClass)
        if (classOf[AuditedModel].isAssignableFrom(modelClass)) {
          auditedModelBindings.addBinding.to(modelClass.asInstanceOf[Class[AuditedModel]])
        }
      }

    reflectionClasses
      .getSubTypesOf(classOf[AuthSrv])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
      .filterNot(c => c == classOf[MultiAuthSrv] || c == classOf[TheHiveAuthSrv])
      .foreach { authSrvClass =>
        authBindings.addBinding.to(authSrvClass)
      }

    reflectionClasses
      .getSubTypesOf(classOf[UserMapper])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
      .filterNot(c => c == classOf[MultiUserMapperSrv])
      .foreach(mapperCls => ssoMapperBindings.addBinding.to(mapperCls))

    bind[MigrationOperations].to[Migration]
    bind[AuthSrv].to[TheHiveAuthSrv]
    bind[UserMapper].to[MultiUserMapperSrv]

    bindActor[AuditActor]("AuditActor", props = _.withDispatcher("auditTask"))
    bindActor[LocalStreamActor]("localStreamActor")

    if (environment.mode == Mode.Prod)
      bind[AssetCtrl].to[AssetCtrlProd]
    else
      bind[AssetCtrl].to[AssetCtrlDev]

    ScalaMultibinder.newSetBinder[Connector](binder)
    ()
  }
}
