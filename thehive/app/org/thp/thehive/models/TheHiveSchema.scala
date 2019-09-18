package org.thp.thehive.models

import java.lang.reflect.Modifier

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}
import play.api.Logger
import play.api.inject.Injector
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{HasModel, InitialValue, Model, Schema}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, RoleSrv, UserSrv}

import scala.util.Try

@Singleton
class TheHiveSchema @Inject()(injector: Injector) extends Schema {

  lazy val logger   = Logger(getClass)
  val rm: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)

  val reflectionClasses = new Reflections(
    new ConfigurationBuilder()
      .forPackages("org.thp.thehive.models")
      .addClassLoader(getClass.getClassLoader)
      .setExpandSuperTypes(true)
      .setScanners(new SubTypesScanner(false))
  )

  override lazy val modelList: Seq[Model] =
    reflectionClasses
      .getSubTypesOf(classOf[HasModel[_]])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .map { modelClass =>
        val hasModel = rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance.asInstanceOf[HasModel[_]]
        logger.info(s"Loading model ${hasModel.model.label}")
        hasModel.model
      }
      .toSeq

  override lazy val initialValues: Seq[InitialValue[_]] =
    reflectionClasses
      .getSubTypesOf(classOf[VertexSrv[_, _]])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .toSeq
      .map { vertexSrvClass =>
        injector.instanceOf(vertexSrvClass).getInitialValues
      }
      .flatten

  override def init(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      adminUser           <- injector.instanceOf[UserSrv].getOrFail(UserSrv.initUser.login)
      adminProfile        <- injector.instanceOf[ProfileSrv].getOrFail(ProfileSrv.admin.name)
      defaultOrganisation <- injector.instanceOf[OrganisationSrv].getOrFail(OrganisationSrv.default.name)
      _                   <- injector.instanceOf[RoleSrv].create(adminUser, defaultOrganisation, adminProfile)
    } yield ()
}
