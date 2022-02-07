package org.thp.thehive.models

import org.janusgraph.graphdb.types.TypeDefinitionCategory
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.models.evolution._
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import java.lang.reflect.Modifier
import scala.jdk.CollectionConverters._
import scala.reflect.runtime.{universe => ru}

object TheHiveSchemaDefinition
    extends Schema
    with UpdatableSchema
    with V4_0_0
    with V4_0_1
    with V4_0_2
    with V4_0_3
    with V4_1_0
    with V4_1_1
    with V4_1_2
    with V4_1_3
    with V4_1_4
    with V4_1_5
    with V4_1_15
    with V4_2_0 {

  // Make sure TypeDefinitionCategory has been initialised before ModifierType to prevent ExceptionInInitializerError
  TypeDefinitionCategory.BACKING_INDEX
  lazy val logger: Logger = Logger(getClass)
  val operations: Operations = {
    val evolution = evolutionV4_0_0 andThen
      evolutionV4_0_1 andThen
      evolutionV4_0_2 andThen
      evolutionV4_0_3 andThen
      evolutionV4_1_0 andThen
      evolutionV4_1_1 andThen
      evolutionV4_1_2 andThen
      evolutionV4_1_3 andThen
      evolutionV4_1_4 andThen
      evolutionV4_1_5 andThen
      evolutionV4_1_15 andThen
      evolutionV4_2_0
    evolution(Operations("thehive"))
  }

  override lazy val modelList: Seq[Model] = {
    val reflectionClasses = new Reflections(
      new ConfigurationBuilder()
        .forPackages("org.thp.thehive.models")
        .addClassLoader(getClass.getClassLoader)
        .setExpandSuperTypes(true)
        .setScanners(new SubTypesScanner(false))
    )
    val rm: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
    reflectionClasses
      .getSubTypesOf(classOf[HasModel])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .map { modelClass =>
        val hasModel = rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance.asInstanceOf[HasModel]
        hasModel.model
      }
      .toSeq
  }

  override val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
}
