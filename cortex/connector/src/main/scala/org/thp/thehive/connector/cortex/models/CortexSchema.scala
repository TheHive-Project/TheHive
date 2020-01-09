package org.thp.thehive.connector.cortex.models

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}

import play.api.Logger

import javax.inject.{Inject, Singleton}
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.models.{HasModel, Model, Schema}

@Singleton
class CortexSchema @Inject()() extends Schema {

  lazy val logger: Logger = Logger(getClass)
  val rm: ru.Mirror       = ru.runtimeMirror(getClass.getClassLoader)
  logger.info(s"Search models in org.thp.thehive.connector.cortex.models")

  lazy val reflectionClasses = new Reflections(
    new ConfigurationBuilder()
      .forPackages("org.thp.thehive.connector.cortex.models")
      .addClassLoaders(getClass.getClassLoader)
      .setExpandSuperTypes(true)
      .setScanners(new SubTypesScanner(false))
  )

  override lazy val modelList: Seq[Model] = {
    reflectionClasses
      .getSubTypesOf(classOf[HasModel[_]])
      .asScala
      .filterNot(c => java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .map { modelClass =>
        val hasModel = rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance.asInstanceOf[HasModel[_]]
        logger.info(s"Loading model ${hasModel.model.label}")
        hasModel.model
      }
      .toSeq
  }
}
