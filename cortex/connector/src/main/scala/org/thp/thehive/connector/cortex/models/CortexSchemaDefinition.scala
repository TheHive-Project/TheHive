package org.thp.thehive.connector.cortex.models

import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}

@Singleton
class CortexSchemaDefinition @Inject() () extends Schema with UpdatableSchema {

  lazy val logger: Logger    = Logger(getClass)
  val operations: Operations = Operations("thehive-cortex").noop

  lazy val reflectionClasses = new Reflections(
    new ConfigurationBuilder()
      .forPackages("org.thp.thehive.connector.cortex.models")
      .addClassLoaders(getClass.getClassLoader)
      .setExpandSuperTypes(true)
      .setScanners(new SubTypesScanner(false))
  )

  override lazy val modelList: Seq[Model] = {
    val rm: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
    reflectionClasses
      .getSubTypesOf(classOf[HasModel])
      .asScala
      .filterNot(c => java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .map(modelClass => rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance)
      .collect {
        case hasModel: HasModel =>
          logger.debug(s"Loading model ${hasModel.model.label}")
          hasModel.model
      }
      .toSeq
  }
  override val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
}
