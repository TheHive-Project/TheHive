package org.thp.thehive.models

import org.apache.tinkerpop.gremlin.process.traversal.{Order, P, TextP}
import org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality
import org.janusgraph.core.schema.ConsistencyModifier
import org.janusgraph.graphdb.types.TypeDefinitionCategory
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.services.LocalUserSrv
import play.api.Logger

import java.lang.reflect.Modifier
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}
import scala.util.{Success, Try}

@Singleton
class TheHiveSchemaDefinition @Inject() extends Schema with UpdatableSchema {

  // Make sure TypeDefinitionCategory has been initialised before ModifierType to prevent ExceptionInInitializerError
  TypeDefinitionCategory.BACKING_INDEX
  lazy val logger: Logger = Logger(getClass)
  val operations: Operations = Operations("thehive")
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
      // TODO remove unused commented code ?
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
    //=====[release 4.0.4]=====
    //=====[release 4.0.5]=====
    // Taxonomies
    .addVertexModel[String]("Taxonomy")
    .addProperty[String]("Taxonomy", "namespace")
    .addProperty[String]("Taxonomy", "description")
    .addProperty[Int]("Taxonomy", "version")
    .dbOperation[Database]("Add Custom taxonomy vertex for each Organisation") { db =>
      db.tryTransaction { implicit graph =>
        // For each organisation, if there is no custom taxonomy, create it
        graph.V("Organisation").unsafeHas("name", P.neq("admin")).foreach { o =>
          val hasFreetagsTaxonomy = graph
            .V("Organisation", EntityId(o.id))
            .out[OrganisationTaxonomy]
            .v[Taxonomy]
            .unsafeHas("namespace", s"_freetags_${o.id()}")
            .exists
          if (!hasFreetagsTaxonomy) {
            val taxoVertex = graph.addVertex("Taxonomy")
            taxoVertex.property("_label", "Taxonomy")
            taxoVertex.property("_createdBy", "system@thehive.local")
            taxoVertex.property("_createdAt", new Date())
            taxoVertex.property("namespace", s"_freetags_${o.id()}")
            taxoVertex.property("description", "Custom taxonomy")
            taxoVertex.property("version", 1)
            o.addEdge("OrganisationTaxonomy", taxoVertex)
          }
        }
        Success(())
      }
    }
    .updateGraph("Add each tag to its Organisation's FreeTags taxonomy", "Tag") { tags =>
      tags
        .project(
          _.by.by(
            _.unionFlat(
              _.in("CaseTag").in("ShareCase").in("OrganisationShare"),
              _.in("ObservableTag").unionFlat(_.in("ShareObservable").in("OrganisationShare"), _.in("AlertObservable").out("AlertOrganisation")),
              _.in("AlertTag").out("AlertOrganisation"),
              _.in("CaseTemplateTag").out("CaseTemplateOrganisation")
            )
              .dedup
              .sort(_.by("_createdAt", Order.desc))
              .limit(1)
              .out("OrganisationTaxonomy")
              .unsafeHas("namespace", TextP.startingWith("_freetags_"))
              .option
          )
        )
        .foreach {
          case (tag, Some(freetagsTaxo)) =>
            val tagStr = tagString(
              tag.property[String]("namespace").value(),
              tag.property[String]("predicate").value(),
              tag.property[String]("value").orElse("")
            )
            tag.property("namespace", freetagsTaxo.property[String]("namespace").value)
            tag.property("predicate", tagStr)
            tag.property("value").remove()
            freetagsTaxo.addEdge("TaxonomyTag", tag)
          case (tag, None) =>
            val tagStr = tagString(
              tag.property[String]("namespace").value(),
              tag.property[String]("predicate").value(),
              tag.property[String]("value").orElse("")
            )
            logger.warn(s"Tag $tagStr is not linked to any organisation")
        }
      Success(())
    }
    .updateGraph("Add manageTaxonomy to admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "admin").raw.property("permissions", "manageTaxonomy").iterate()
      Success(())
    }
    .updateGraph("Remove colour property for Tags", "Tag") { traversal =>
      traversal.removeProperty("colour").iterate()
      Success(())
    }
    .removeProperty("Tag", "colour", usedOnlyByThisModel = true)
    .addProperty[String]("Tag", "colour")
    .updateGraph("Add property colour for Tags ", "Tag") { traversal =>
      traversal.raw.property("colour", "#000000").iterate()
      Success(())
    }
    // Patterns
    .updateGraph("Add managePattern permission to admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "admin").raw.property("permissions", "managePattern").iterate()
      Success(())
    }
    .updateGraph("Add manageProcedure permission to org-admin and analyst profiles", "Profile") { traversal =>
      traversal
        .unsafeHas("name", P.within("org-admin", "analyst"))
        .raw
        .property("permissions", "manageProcedure")
        .iterate()
      Success(())
    }
    // Index backend
    /* Alert index  */
    .addProperty[Seq[String]]("Alert", "tags")
    .addProperty[EntityId]("Alert", "organisationId")
    .addProperty[Option[EntityId]]("Alert", "caseId")
    .updateGraph("Add tags, organisationId and caseId in alerts", "Alert") { traversal =>
      traversal
        .project(
          _.by
            .by(_.out("AlertTag").valueMap("namespace", "predicate", "value").fold)
            .by(_.out("AlertOrganisation")._id.option)
            .by(_.out("AlertCase")._id.option)
        )
        .foreach {
          case (vertex, tagMaps, Some(organisationId), caseId) =>
            val tags = for {
              tag <- tagMaps.asInstanceOf[Seq[Map[String, String]]]
              namespace = tag.getOrElse("namespace", "_autocreate")
              predicate <- tag.get("predicate")
              value = tag.get("value")
            } yield
              (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
                (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
                value.fold("")(v => f"""="$v"""")

            tags.foreach(vertex.property(Cardinality.list, "tags", _))
            vertex.property("organisationId", organisationId.value)
            caseId.foreach(vertex.property("caseId", _))
          case _ =>
        }
      Success(())
    }
    /* Case index  */
    .addProperty[Seq[String]]("Case", "tags")
    .addProperty[Option[String]]("Case", "assignee")
    .addProperty[Set[EntityId]]("Case", "organisationIds")
    .addProperty[Option[String]]("Case", "impactStatus")
    .addProperty[Option[String]]("Case", "resolutionStatus")
    .addProperty[Option[String]]("Case", "caseTemplate")
    .updateGraph("Add tags, assignee, organisationIds, impactStatus, resolutionStatus and caseTemplate data in cases", "Case") { traversal =>
      traversal
        .project(
          _.by
            .by(_.out("CaseTag").valueMap("namespace", "predicate", "value").fold)
            .by(_.out("CaseUser").property("login", UMapping.string).option)
            .by(_.in("ShareCase").in("OrganisationShare")._id.fold)
            .by(_.out("CaseImpactStatus").property("value", UMapping.string).option)
            .by(_.out("CaseResolutionStatus").property("value", UMapping.string).option)
            .by(_.out("CaseCaseTemplate").property("name", UMapping.string).option)
        )
        .foreach {
          case (vertex, tagMaps, assignee, organisationIds, impactStatus, resolutionStatus, caseTemplate) =>
            val tags = for {
              tag <- tagMaps.asInstanceOf[Seq[Map[String, String]]]
              namespace = tag.getOrElse("namespace", "_autocreate")
              predicate <- tag.get("predicate")
              value = tag.get("value")
            } yield
              (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
                (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
                value.fold("")(v => f"""="$v"""")

            tags.foreach(vertex.property(Cardinality.list, "tags", _))
            assignee.foreach(vertex.property("assignee", _))
            organisationIds.foreach(id => vertex.property(Cardinality.set, "organisationIds", id.value))
            impactStatus.foreach(vertex.property("impactStatus", _))
            resolutionStatus.foreach(vertex.property("resolutionStatus", _))
            caseTemplate.foreach(vertex.property("caseTemplate", _))
        }
      Success(())
    }
    /* CaseTemplate index  */
    .addProperty[Seq[String]]("CaseTemplate", "tags")
    .updateGraph("Add tags in caseTempates", "CaseTemplate") { traversal =>
      traversal
        .project(
          _.by
            .by(_.out("CaseTemplateTag").valueMap("namespace", "predicate", "value").fold)
        )
        .foreach {
          case (vertex, tagMaps) =>
            val tags = for {
              tag <- tagMaps.asInstanceOf[Seq[Map[String, String]]]
              namespace = tag.getOrElse("namespace", "_autocreate")
              predicate <- tag.get("predicate")
              value = tag.get("value")
            } yield
              (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
                (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
                value.fold("")(v => f"""="$v"""")

            tags.foreach(vertex.property(Cardinality.list, "tags", _))
        }
      Success(())
    }
    /* Log index */
    .addProperty[String]("Log", "taskId")
    .addProperty[Set[EntityId]]("Log", "organisationIds")
    .updateGraph("Add taskId and organisationIds data in logs", "Log") { traversal =>
      traversal
        .project(
          _.by
            .by(_.in("TaskLog")._id)
            .by(_.in("TaskLog").in("ShareTask").in("OrganisationShare")._id.fold)
        )
        .foreach {
          case (vertex, taskId, organisationIds) =>
            vertex.property("taskId", taskId)
            organisationIds.foreach(id => vertex.property(Cardinality.set, "organisationIds", id.value))
        }
      Success(())
    }
    /* Observable index */
    .addProperty[String]("Observable", "dataType")
    .addProperty[Seq[String]]("Observable", "tags")
    .addProperty[String]("Observable", "data")
    .addProperty[EntityId]("Observable", "relatedId")
    .addProperty[Set[EntityId]]("Observable", "organisationIds")
    .updateGraph("Add dataType, tags, data, relatedId and organisationIds data in observables", "Observable") { traversal =>
      traversal
        .project(
          _.by
            .by(_.out("ObservableObservableType").property("name", UMapping.string))
            .by(_.out("ObservableTag").valueMap("namespace", "predicate", "value").fold)
            .by(_.out("ObservableData").property("data", UMapping.string).option)
            .by(_.out("ObservableAttachment").property("attachmentId", UMapping.string).option)
            .by(_.coalesceIdent(_.in("ShareObservable").out("ShareCase"), _.in("AlertObservable"), _.in("ReportObservable"))._id.option)
            .by(
              _.coalesceIdent(
                _.optional(_.in("ReportObservable").in("ObservableJob")).in("ShareObservable").in("OrganisationShare"),
                _.in("AlertObservable").out("AlertOrganisation")
              )
                ._id
                .fold
            )
        )
        .foreach {
          case (vertex, dataType, tagMaps, data, attachmentId, Some(relatedId), organisationIds) =>
            val tags = for {
              tag <- tagMaps.asInstanceOf[Seq[Map[String, String]]]
              namespace = tag.getOrElse("namespace", "_autocreate")
              predicate <- tag.get("predicate")
              value = tag.get("value")
            } yield
              (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
                (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
                value.fold("")(v => f"""="$v"""")

            vertex.property("dataType", dataType)
            tags.foreach(vertex.property(Cardinality.list, "tags", _))
            data.foreach(vertex.property("data", _))
            attachmentId.foreach(vertex.property("attachmentId", _))
            vertex.property("relatedId", relatedId.value)
            organisationIds.foreach(id => vertex.property(Cardinality.set, "organisationIds", id.value))
          case _ =>
        }
      Success(())
    }
    /* Task index */
    .addProperty[Option[String]]("Task", "assignee")
    .addProperty[Set[EntityId]]("Task", "organisationIds")
    .addProperty[EntityId]("Task", "relatedId")
    .updateGraph("Add assignee, relatedId and organisationIds data in tasks", "Task") { traversal =>
      traversal
        .project(
          _.by
            .by(_.out("TaskUser").property("login", UMapping.string).option)
            .by(_.coalesceIdent(_.in("ShareTask").out("ShareCase"), _.in("CaseTemplateTask"))._id.option)
            .by(_.coalesceIdent(_.in("ShareTask").in("OrganisationShare"), _.in("CaseTemplateTask").out("CaseTemplateOrganisation"))._id.fold)
        )
        .foreach {
          case (vertex, assignee, Some(relatedId), organisationIds) =>
            assignee.foreach(vertex.property("assignee", _))
            vertex.property("relatedId", relatedId.value)
            organisationIds.foreach(id => vertex.property(Cardinality.set, "organisationIds", id.value))
          case _ =>
        }
      Success(())
    }
    .updateGraph("Add managePlatform permission to admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "admin").raw.property("permissions", "managePlatform").iterate()
      Success(())
    }
    .updateGraph("Remove manageTag permission to admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "admin").raw.properties[String]("permissions").forEachRemaining(p => if (p.value() == "manageTag") p.remove())
      Success(())
    }
    .updateGraph("Add manageTag permission to org-admin profile", "Profile") { traversal =>
      traversal.unsafeHas("name", "org-admin").raw.property("permissions", "manageTag").iterate()
      Success(())
    }
    .updateGraph("Remove deleted logs and deleted property from logs", "Log") { traversal =>
      traversal.clone().unsafeHas("deleted", "true").remove()
      traversal.removeProperty("deleted")
      Success(())
    }
    .removeProperty(model = "Log", propertyName = "deleted", usedOnlyByThisModel = true)
    .updateGraph("Make shared dashboard writable", "Dashboard") { traversal =>
      traversal.outE("OrganisationDashboard").raw.property("writable", true).iterate()
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

  override val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
}
