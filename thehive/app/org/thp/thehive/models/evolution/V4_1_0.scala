package org.thp.thehive.models.evolution

import org.apache.tinkerpop.gremlin.process.traversal.{Order, P}
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.{Database, Operations, TextPredicate, UMapping}
import org.thp.scalligraph.services.ElementOps
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.models.TheHiveSchemaDefinition.logger
import org.thp.thehive.models.{OrganisationTaxonomy, Taxonomy}

import java.util.Date
import scala.util.Success

trait V4_1_0 extends TraversalOps with ElementOps {
  def evolutionV4_1_0: Operations => Operations =
    // Taxonomies
    _.addVertexModel("Taxonomy")
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
              taxoVertex.setProperty("_label", "Taxonomy")
              taxoVertex.setProperty("_createdBy", "system@thehive.local")
              taxoVertex.setProperty("_createdAt", new Date())
              taxoVertex.setProperty("namespace", s"_freetags_${o.id()}")
              taxoVertex.setProperty("description", "Custom taxonomy")
              taxoVertex.setProperty("version", 1)
              o.addEdge("OrganisationTaxonomy", taxoVertex)
            }
          }
          Success(())
        }
      }
      .updateGraphVertices("Add each tag to its Organisation's FreeTags taxonomy", "Tag") { tags =>
        tags
          .unsafeHas("namespace", TextPredicate.notStartsWith("_freetags_"))
          .project(
            _.by
              .by(
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
                  .unsafeHas("namespace", TextPredicate.startsWith("_freetags_"))
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
      .updateGraphVertices("Add manageTaxonomy to admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "admin").raw.property("permissions", "manageTaxonomy").iterate()
        Success(())
      }
      .updateGraphVertices("Remove colour property for Tags", "Tag") { traversal =>
        traversal.removeProperty("colour").iterate()
        Success(())
      }
      .removeProperty[Int]("Tag", "colour", usedOnlyByThisModel = true)
      .addProperty[String]("Tag", "colour")
      .updateGraphVertices("Add property colour for Tags ", "Tag") { traversal =>
        traversal.raw.property("colour", "#000000").iterate()
        Success(())
      }
      // Patterns
      .updateGraphVertices("Add managePattern permission to admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "admin").raw.property("permissions", "managePattern").iterate()
        Success(())
      }
      .noop
      //    .updateGraph("Add manageProcedure permission to org-admin and analyst profiles", "Profile") { traversal =>
      //      traversal
      //        .unsafeHas("name", P.within("org-admin", "analyst"))
      //        .raw
      //        .property("permissions", "manageProcedure")
      //        .iterate()
      //      Success(())
      //    }
      // Index backend
      /* Alert index  */
      .addProperty[Seq[String]]("Alert", "tags")
      .addProperty[EntityId]("Alert", "organisationId")
      .addProperty[Option[EntityId]]("Alert", "caseId")
      .updateGraphVertices("Add tags, organisationId and caseId in alerts", "Alert") { traversal =>
        traversal
          .unsafeHasNot("organisationId")
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

              vertex.setProperty("tags", tags)
              vertex.setProperty("organisationId", organisationId)
              vertex.setProperty("caseId", caseId)
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
      .updateGraphVertices("Add tags, assignee, organisationIds, impactStatus, resolutionStatus and caseTemplate data in cases", "Case") {
        traversal =>
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

                vertex.setProperty("tags", tags)
                vertex.setProperty("assignee", assignee)
                vertex.setProperty("organisationIds", organisationIds.toSet)
                vertex.setProperty("impactStatus", impactStatus)
                vertex.setProperty("resolutionStatus", resolutionStatus)
                vertex.setProperty("caseTemplate", caseTemplate)
            }
          Success(())
      }
      /* CaseTemplate index  */
      .addProperty[Seq[String]]("CaseTemplate", "tags")
      .updateGraphVertices("Add tags in caseTemplates", "CaseTemplate") { traversal =>
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

              vertex.setProperty("tags", tags)
          }
        Success(())
      }
      /* Log index */
      .addProperty[String]("Log", "taskId")
      .addProperty[Set[EntityId]]("Log", "organisationIds")
      .updateGraphVertices("Add taskId and organisationIds data in logs", "Log") { traversal =>
        traversal
          .unsafeHasNot("organisationIds")
          .project(
            _.by
              .by(_.in("TaskLog")._id.option)
              .by(_.in("TaskLog").in("ShareTask").in("OrganisationShare")._id.fold)
          )
          .foreach {
            case (vertex, taskId, organisationIds) =>
              vertex.setProperty("taskId", taskId)
              vertex.setProperty("organisationIds", organisationIds.toSet)
          }
        Success(())
      }
      /* Observable index */
      .addProperty[String]("Observable", "dataType")
      .addProperty[Seq[String]]("Observable", "tags")
      .addProperty[String]("Observable", "data")
      .addProperty[EntityId]("Observable", "relatedId")
      .addProperty[Set[EntityId]]("Observable", "organisationIds")
      .updateGraphVertices("Add dataType, tags, data, relatedId and organisationIds data in observables", "Observable") { traversal =>
        traversal
          .unsafeHasNot("organisationIds")
          .project(
            _.by
              .by(_.out("ObservableObservableType").property("name", UMapping.string).option)
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

              vertex.setProperty("dataType", dataType)
              vertex.setProperty("tags", tags)
              vertex.setProperty("data", data)
              vertex.setProperty("attachmentId", attachmentId)
              vertex.setProperty("relatedId", relatedId)
              vertex.setProperty("organisationIds", organisationIds.toSet)
            case _ =>
          }
        Success(())
      }
      /* Task index */
      .addProperty[Option[String]]("Task", "assignee")
      .addProperty[Set[EntityId]]("Task", "organisationIds")
      .addProperty[EntityId]("Task", "relatedId")
      .updateGraphVertices("Add assignee, relatedId and organisationIds data in tasks", "Task") { traversal =>
        traversal
          .unsafeHasNot("organisationIds")
          .project(
            _.by
              .by(_.out("TaskUser").property("login", UMapping.string).option)
              .by(_.coalesceIdent(_.in("ShareTask").out("ShareCase"), _.in("CaseTemplateTask"))._id.option)
              .by(_.coalesceIdent(_.in("ShareTask").in("OrganisationShare"), _.in("CaseTemplateTask").out("CaseTemplateOrganisation"))._id.fold)
          )
          .foreach {
            case (vertex, assignee, Some(relatedId), organisationIds) =>
              vertex.setProperty("assignee", assignee)
              vertex.setProperty("relatedId", relatedId)
              vertex.setProperty("organisationIds", organisationIds.toSet)
            case _ =>
          }
        Success(())
      }
      .updateGraphVertices("Add managePlatform permission to admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "admin").raw.property("permissions", "managePlatform").iterate()
        Success(())
      }
      .updateGraphVertices("Remove manageTag permission to admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "admin").raw.properties[String]("permissions").forEachRemaining(p => if (p.value() == "manageTag") p.remove())
        Success(())
      }
      .updateGraphVertices("Add manageTag permission to org-admin profile", "Profile") { traversal =>
        traversal.unsafeHas("name", "org-admin").raw.property("permissions", "manageTag").iterate()
        Success(())
      }
      .updateGraphVertices("Remove deleted logs and deleted property from logs", "Log") { traversal =>
        traversal.clone().unsafeHas("deleted", "true").remove()
        traversal.removeProperty("deleted")
        Success(())
      }
      .removeProperty[Boolean](model = "Log", propertyName = "deleted", usedOnlyByThisModel = true)
      .updateGraphVertices("Make shared dashboard writable", "Dashboard") { traversal =>
        traversal.outE("OrganisationDashboard").raw.property("writable", true).iterate()
        Success(())
      }

  private def tagString(namespace: String, predicate: String, value: String): String =
    (if (namespace.headOption.getOrElse('_') == '_') "" else namespace + ':') +
      (if (predicate.headOption.getOrElse('_') == '_') "" else predicate) +
      (if (value.isEmpty) "" else f"""="$value"""")
}
