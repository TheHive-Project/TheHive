package org.thp.thehive.models

import java.lang.reflect.Modifier
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality
import org.janusgraph.core.schema.ConsistencyModifier
import org.janusgraph.graphdb.types.TypeDefinitionCategory
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}
import scala.util.{Success, Try}

@Singleton
class TheHiveSchemaDefinition @Inject() extends Schema with UpdatableSchema {

  // Make sure TypeDefinitionCategory has been initialised before ModifierType to prevent ExceptionInInitializerError
  TypeDefinitionCategory.BACKING_INDEX
  lazy val logger: Logger = Logger(getClass)
  val name: String        = "thehive"
  val operations: Operations = Operations(name)
    .addProperty[Option[Boolean]]("Observable", "seen")
    .updateGraph("Add manageConfig permission to org-admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "org-admin").raw.property("permissions", "manageConfig").iterate()
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
    .noop // .addIndex("CustomField", IndexType.unique, "name")
    .dbOperation[JanusDatabase]("Remove locks") { db =>
      def removePropertyLock(name: String) =
        db.managementTransaction { mgmt =>
          Try(mgmt.setConsistency(mgmt.getPropertyKey(name), ConsistencyModifier.DEFAULT))
            .recover {
              case error => logger.warn(s"Unable to remove lock on property $name: $error")
            }
        }
      // def removeIndexLock(name: String): Try[Unit] =
      //   db.managementTransaction { mgmt =>
      //     Try(mgmt.setConsistency(mgmt.getGraphIndex(name), ConsistencyModifier.DEFAULT))
      //       .recover {
      //         case error => logger.warn(s"Unable to remove lock on index $name: $error")
      //       }
      //   }

      // removeIndexLock("CaseNumber")
      removePropertyLock("number")
      // removeIndexLock("DataData")
      removePropertyLock("data")
    }
    .noop // .addIndex("Tag", IndexType.unique, "namespace", "predicate", "value")
    .noop // .addIndex("Audit", IndexType.basic, "requestId", "mainAction")
    .rebuildIndexes
    //=====[release 4.0.0]=====
    .updateGraph("Remove cases with a Deleted status", "Case") { traversal =>
      traversal.unsafeHas("status", "Deleted").remove()
      Success(())
    }
    .addProperty[Option[Boolean]]("Observable", "ignoreSimilarity")
    //=====[release 4.0.1]=====
    .updateGraph("Add accessTheHiveFS permission to analyst and org-admin profiles", "Profile") { traversal =>
      traversal
        .unsafeHas("name", P.within("org-admin", "analyst"))
        .onRaw(_.property(Cardinality.set: Cardinality, "permissions", "accessTheHiveFS", Nil: _*)) // Nil is for disambiguate the overloaded methods
        .iterate()
      Success(())
    }
    //=====[release 4.0.2]=====
    .addProperty[Boolean]("ShareTask", "actionRequired")
    .updateGraph("Add actionRequire property", "Share") { traversal =>
      traversal.outE[ShareTask].raw.property("actionRequired", false).iterate()
      Success(())
    }
    //=====[release 4.0.3]=====
    .addProperty[String]("Alert", "organisationId")
    .updateGraph("Add organisation data in alerts", "Alert") { traversal =>
      traversal
        .project(_.by.by(_.out("AlertOrganisation")._id))
        .toIterator
        .foreach {
          case (vertex, organisationId) =>
            vertex.property("organisationId", organisationId.value)
        }
      Success(())
    }
    .addProperty[Seq[String]]("Case", "organisationIds")
    .updateGraph("Add organisation data in cases", "Case") { traversal =>
      traversal
        .project(_.by.by(_.in("ShareCase").in("OrganisationShare")._id.fold))
        .toIterator
        .foreach {
          case (vertex, organisationIds) =>
            organisationIds.foreach(id => vertex.property(Cardinality.list, "organisationIds", id.value))
        }
      Success(())
    }
    .addProperty[Seq[String]]("Observable", "organisationIds")
    .updateGraph("Add organisation data in observables", "Observable") { traversal =>
      traversal
        .project(
          _.by
            .by(
              _.coalesceIdent(
                _.optional(_.in("ReportObservable").in("ObservableJob")).in("ShareObservable").in("OrganisationShare"),
                _.in("AlertObservable").out("AlertOrganisation")
              )
                ._id
                .fold
            )
        )
        .toIterator
        .foreach {
          case (vertex, organisationIds) =>
            organisationIds.foreach(id => vertex.property(Cardinality.list, "organisationIds", id.value))
        }
      Success(())
    }
    .rebuildIndexes

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
      .getSubTypesOf(classOf[HasModel])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .map { modelClass =>
        val hasModel = rm.reflectModule(rm.classSymbol(modelClass).companion.companion.asModule).instance.asInstanceOf[HasModel]
        logger.info(s"Loading model ${hasModel.model.label}")
        hasModel.model
      }
      .toSeq
  }

  override lazy val initialValues: Seq[InitialValue[_]] = modelList.collect {
    case vertexModel: VertexModel => vertexModel.getInitialValues
  }.flatten

  override def init(db: Database)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = Success(())

  override val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
}
