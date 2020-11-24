package org.thp.thehive.models

import java.lang.reflect.Modifier
import java.util.Date

import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality
import org.janusgraph.core.schema.ConsistencyModifier
import org.janusgraph.graphdb.types.TypeDefinitionCategory
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.{EntityId, RichSeq}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
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
      Try(traversal.unsafeHas("name", "org-admin").raw.property("permissions", "manageConfig").iterate())
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
    // Taxonomies
    .addVertexModel[String]("Taxonomy", Seq("namespace"))
    .dbOperation[Database]("Add Custom taxonomy vertex for each Organisation") { db =>
      db.tryTransaction { implicit g =>
        // For each organisation, if there is no custom taxonomy, create it
        db.labelFilter("Organisation")(Traversal.V()).unsafeHas("name", P.neq("admin")).toIterator.toTry { o =>
          Traversal.V(EntityId(o.id)).out[OrganisationTaxonomy].v[Taxonomy].unsafeHas("namespace", "_freetags").headOption match {
            case None =>
              val taxoVertex = g.addVertex("Taxonomy")
              taxoVertex.property("_label", "Taxonomy")
              taxoVertex.property("_createdBy", "system@thehive.local")
              taxoVertex.property("_createdAt", new Date())
              taxoVertex.property("namespace", "_freetags")
              taxoVertex.property("description", "Custom taxonomy")
              taxoVertex.property("version", 1)
              taxoVertex.property("enabled", true)
              o.addEdge("OrganisationTaxonomy", taxoVertex)
              Success(())
            case _ => Success(())
          }
        }
      }.map(_ => ())
    }
    .dbOperation[Database]("Add each tag to its Organisation's Custom taxonomy") { db =>
      db.tryTransaction { implicit g =>
        db.labelFilter("Organisation")(Traversal.V()).unsafeHas("name", P.neq("admin")).toIterator.toTry { o =>
          val customTaxo = Traversal.V(EntityId(o.id())).out("OrganisationTaxonomy").unsafeHas("namespace", "_freetags").head
          Traversal.V(EntityId(o.id())).unionFlat(
            _.out("OrganisationShare").out("ShareCase").out("CaseTag"),
            _.out("OrganisationShare").out("ShareObservable").out("ObservableTag"),
            _.in("AlertOrganisation").out("AlertTag"),
            _.in("CaseTemplateOrganisation").out("CaseTemplateTag")
          ).toSeq.foreach { tag =>
            // Create a freetext tag and store it into predicate
            val tagStr = tagString(
              tag.property("namespace").value().toString,
              tag.property("predicate").value().toString,
              tag.property ("value").orElse("")
            )
            tag.property("namespace", "_freetags")
            tag.property("predicate", tagStr)
            tag.property("value").remove()
            customTaxo.addEdge("TaxonomyTag", tag)
          }
          Success(())
        }
      }.map(_ => ())
    }
    .updateGraph("Add manageTaxonomy to admin profile", "Profile") { traversal =>
      Try(traversal.unsafeHas("name", "admin").raw.property("permissions", "manageTaxonomy").iterate())
      Success(())
    }

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

  private def tagString(namespace: String, predicate: String, value: String): String =
    (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
    (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
    (if (value.isEmpty) "" else f"""="$value"""")

  override def init(db: Database)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = Success(())
}
