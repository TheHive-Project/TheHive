package org.thp.thehive.connector.cortex.models

import javax.inject.{Inject, Singleton}
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.models._
import play.api.Logger

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}

@Singleton
class CortexSchemaDefinition @Inject() () extends Schema with UpdatableSchema {

  lazy val logger: Logger    = Logger(getClass)
  val name: String           = "thehive-cortex"
  val operations: Operations = Operations(name)

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
      .getSubTypesOf(classOf[HasModel[_]])
      .asScala
      .filterNot(c => java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .map(modelClass => rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance)
      .collect {
        case hasModel: HasModel[_] =>
          logger.info(s"Loading model ${hasModel.model.label}")
          hasModel.model
      }
      .toSeq
  }
}
