package org.thp.thehive.models

import java.lang.reflect.Modifier

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}
import scala.util.{Success, Try}
import play.api.Logger
import play.api.inject.Injector
import gremlin.scala.{Graph, Key}
import javax.inject.{Inject, Singleton}
import org.janusgraph.core.schema.ConsistencyModifier
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{HasModel, IndexType, InitialValue, Model, Operations, Schema, UpdatableSchema}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, RoleSrv, UserSrv}
import org.thp.scalligraph.steps.StepsOps._

@Singleton
class TheHiveSchema @Inject() (injector: Injector) extends Schema with UpdatableSchema {

  lazy val logger: Logger = Logger(getClass)
  val name: String        = "thehive"
  val operations: Operations = Operations(name)
    .addProperty[Option[Boolean]]("Observable", "seen")
    .updateGraph("Add manageConfig permission to org-admin profile", "Profile") { traversal =>
      Try(traversal.has("name", "org-admin").raw.property(Key("permissions") -> "manageConfig").iterate())
      Success(())
    }
    .updateGraph("Remove duplicate custom fields", "CustomField") { traversal =>
      traversal.toIterator.foldLeft(Set.empty[String]) { (names, vertex) =>
        val name = vertex.value[String]("name")
        if (names.contains(name)) {
          vertex.remove()
          names
        } else
          names + name
      }
      Success(())
    }
    .addIndex("CustomField", IndexType.unique, "name")
    .dbOperation[JanusDatabase]("Remove locks") { db =>
      def removePropertyLock(name: String) =
        db.managementTransaction { mgmt =>
          Try(mgmt.setConsistency(mgmt.getPropertyKey(name), ConsistencyModifier.DEFAULT))
            .recover {
              case error => logger.warn(s"Unable to remove lock on property $name: $error")
            }
        }
      def removeIndexLock(name: String) =
        db.managementTransaction { mgmt =>
          Try(mgmt.setConsistency(mgmt.getGraphIndex(name), ConsistencyModifier.DEFAULT))
            .recover {
              case error => logger.warn(s"Unable to remove lock on index $name: $error")
            }
        }

      removeIndexLock("CaseNumber")
      removePropertyLock("number")
      removeIndexLock("DataData")
      removePropertyLock("data")
    }
    .addIndex("Tag", IndexType.tryUnique, "namespace", "predicate", "value")
    .dbOperation[JanusDatabase]("Enable indexes")(_.enableIndexes())

  val reflectionClasses = new Reflections(
    new ConfigurationBuilder()
      .forPackages("org.thp.thehive.models")
      .addClassLoader(getClass.getClassLoader)
      .setExpandSuperTypes(true)
      .setScanners(new SubTypesScanner(false))
  )

  override lazy val modelList: Seq[Model] = {
    val rm: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
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
  }

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
      adminUser         <- injector.instanceOf[UserSrv].getOrFail(UserSrv.init.login)
      adminProfile      <- injector.instanceOf[ProfileSrv].getOrFail(ProfileSrv.admin.name)
      adminOrganisation <- injector.instanceOf[OrganisationSrv].getOrFail(OrganisationSrv.administration.name)
      _                 <- injector.instanceOf[RoleSrv].create(adminUser, adminOrganisation, adminProfile)
    } yield ()
}
