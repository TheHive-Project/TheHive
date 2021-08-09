package org.thp.thehive.models.evolution

import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.models.{IndexType, Operations}
import org.thp.scalligraph.services.ElementOps
import org.thp.scalligraph.traversal.{StepLabel, TraversalOps}

import java.util.Date
import scala.util.Success

trait V4_2_0 extends TraversalOps with ElementOps {
  def evolutionV4_2_0: Operations => Operations =
    _.addProperty[String]("Share", "taskRule")
      .updateGraphVertices("Add taskRule in share", "Share") { traversal =>
        traversal.foreach(_.property("taskRule", "manual"))
        Success(())
      }
      .addProperty[String]("Share", "observableRule")
      .updateGraphVertices("Add observableRule in share", "Share") { traversal =>
        traversal.foreach(_.property("observableRule", "manual"))
        Success(())
      }
      .addProperty[String]("Organisation", "taskRule")
      .updateGraphVertices("Add taskRule in share", "Organisation") { traversal =>
        traversal.foreach(_.property("taskRule", "manual"))
        Success(())
      }
      .addProperty[String]("Organisation", "observableRule")
      .updateGraphVertices("Add observableRule in share", "Organisation") { traversal =>
        traversal.foreach(_.property("observableRule", "manual"))
        Success(())
      }
      .addProperty[String]("OrganisationOrganisation", "linkType")
      .updateGraphVertices("Add linkType in organisation edges", "Organisation") { traversal =>
        traversal.inE("OrganisationOrganisation").foreach(_.property("linkType", "default"))
        Success(())
      }
      .addVertexModel("CustomFieldValue")
      .addIndexedProperty[EntityId]("CustomFieldValue", "elementId", IndexType.standard)
      .addIndexedProperty[String]("CustomFieldValue", "name", IndexType.standard)
      .addIndexedProperty[Option[Int]]("CustomFieldValue", "order", IndexType.standard)
      .addIndexedProperty[Option[String]]("CustomFieldValue", "stringValue", IndexType.standard)
      .addIndexedProperty[Option[Boolean]]("CustomFieldValue", "booleanValue", IndexType.standard)
      .addIndexedProperty[Option[Int]]("CustomFieldValue", "integerValue", IndexType.standard)
      .addIndexedProperty[Option[Double]]("CustomFieldValue", "floatValue", IndexType.standard)
      .addIndexedProperty[Option[Date]]("CustomFieldValue", "dateValue", IndexType.standard)
      .updateGraphVertices("Add vertex for each case custom fields", "CustomField") { traversal =>
        val customFieldLabel      = StepLabel.identity[Vertex]
        val customFieldLabelValue = StepLabel.identity[Edge]
        val elementLabel          = StepLabel.identity[Vertex]
        traversal
          .as(customFieldLabel)
          .inE("CaseCustomField")
          .as(customFieldLabelValue)
          .outV
          .as(elementLabel)
          .select((customFieldLabel, customFieldLabelValue, elementLabel))
          .foreach {
            case (cf, cfv, e) =>
              val vertex = traversal
                .graph
                .addVertex("CustomFieldValue")
                .setProperty("_label", "CustomFieldValue")
                .setProperty("_createdAt", cfv.getProperty[Date]("_createdAt"))
                .setProperty("_createdBy", cfv.getProperty[String]("_createdBy"))
                .setProperty[EntityId]("elementId", EntityId(e.id()))
                .setProperty("name", cf.getProperty[String]("name"))
                .setProperty("order", cfv.getProperty[Option[Int]]("order"))
                .setProperty("stringValue", cfv.getProperty[Option[String]]("stringValue"))
                .setProperty("booleanValue", cfv.getProperty[Option[Boolean]]("booleanValue"))
                .setProperty("integerValue", cfv.getProperty[Option[Int]]("integerValue"))
                .setProperty("floatValue", cfv.getProperty[Option[Double]]("floatValue"))
                .setProperty("dateValue", cfv.getProperty[Option[Date]]("dateValue"))
              vertex.addEdge("CustomFieldValueCustomField", cf)
              e.addEdge("CaseCustomFieldValue", vertex)
          }
        Success(())

      }
      .updateGraphVertices("Remove edge of case custom fields", "CustomField") { traversal =>
        traversal.inE("CaseCustomField").remove()
        Success(())
      }
      .updateGraphVertices("Add vertex for each alert custom fields", "CustomField") { traversal =>
        val customFieldLabel      = StepLabel.identity[Vertex]
        val customFieldLabelValue = StepLabel.identity[Edge]
        val elementLabel          = StepLabel.identity[Vertex]
        traversal
          .as(customFieldLabel)
          .inE("AlertCustomField")
          .as(customFieldLabelValue)
          .outV
          .as(elementLabel)
          .select((customFieldLabel, customFieldLabelValue, elementLabel))
          .foreach {
            case (cf, cfv, e) =>
              val vertex = traversal
                .graph
                .addVertex("CustomFieldValue")
                .setProperty("_label", "CustomFieldValue")
                .setProperty("_createdAt", cfv.getProperty[Date]("_createdAt"))
                .setProperty("_createdBy", cfv.getProperty[String]("_createdBy"))
                .setProperty[EntityId]("elementId", EntityId(e.id()))
                .setProperty("name", cf.getProperty[String]("name"))
                .setProperty("order", cfv.getProperty[Option[Int]]("order"))
                .setProperty("stringValue", cfv.getProperty[Option[String]]("stringValue"))
                .setProperty("booleanValue", cfv.getProperty[Option[Boolean]]("booleanValue"))
                .setProperty("integerValue", cfv.getProperty[Option[Int]]("integerValue"))
                .setProperty("floatValue", cfv.getProperty[Option[Double]]("floatValue"))
                .setProperty("dateValue", cfv.getProperty[Option[Date]]("dateValue"))
              vertex.addEdge("CustomFieldValueCustomField", cf)
              e.addEdge("AlertCustomFieldValue", vertex)
          }
        Success(())

      }
      .updateGraphVertices("Remove edge of alert custom fields", "CustomField") { traversal =>
        traversal.inE("AlertCustomField").remove()
        Success(())
      }
      .updateGraphVertices("Add vertex for each case template custom fields", "CustomField") { traversal =>
        val customFieldLabel      = StepLabel.identity[Vertex]
        val customFieldLabelValue = StepLabel.identity[Edge]
        val elementLabel          = StepLabel.identity[Vertex]
        traversal
          .as(customFieldLabel)
          .inE("CaseTemplateCustomField")
          .as(customFieldLabelValue)
          .outV
          .as(elementLabel)
          .select((customFieldLabel, customFieldLabelValue, elementLabel))
          .foreach {
            case (cf, cfv, e) =>
              val vertex = traversal
                .graph
                .addVertex("CustomFieldValue")
                .setProperty("_label", "CustomFieldValue")
                .setProperty("_createdAt", cfv.getProperty[Date]("_createdAt"))
                .setProperty("_createdBy", cfv.getProperty[String]("_createdBy"))
                .setProperty[EntityId]("elementId", EntityId(e.id()))
                .setProperty("name", cf.getProperty[String]("name"))
                .setProperty("order", cfv.getProperty[Option[Int]]("order"))
                .setProperty("stringValue", cfv.getProperty[Option[String]]("stringValue"))
                .setProperty("booleanValue", cfv.getProperty[Option[Boolean]]("booleanValue"))
                .setProperty("integerValue", cfv.getProperty[Option[Int]]("integerValue"))
                .setProperty("floatValue", cfv.getProperty[Option[Double]]("floatValue"))
                .setProperty("dateValue", cfv.getProperty[Option[Date]]("dateValue"))
              vertex.addEdge("CustomFieldValueCustomField", cf)
              e.addEdge("CaseTemplateCustomFieldValue", vertex)
          }
        Success(())

      }
      .updateGraphVertices("Remove edge of case template custom fields", "CustomField") { traversal =>
        traversal.inE("CaseTemplateCustomField").remove()
        Success(())
      }
      .removeProperty[Option[Int]]("AlertCustomFieldValue", "order", usedOnlyByThisModel = false)
      .removeProperty[Option[String]]("AlertCustomFieldValue", "stringValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Boolean]]("AlertCustomFieldValue", "booleanValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Int]]("AlertCustomFieldValue", "integerValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Double]]("AlertCustomFieldValue", "floatValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Date]]("AlertCustomFieldValue", "dateValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Int]]("CaseCustomFieldValue", "order", usedOnlyByThisModel = false)
      .removeProperty[Option[String]]("CaseCustomFieldValue", "stringValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Boolean]]("CaseCustomFieldValue", "booleanValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Int]]("CaseCustomFieldValue", "integerValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Double]]("CaseCustomFieldValue", "floatValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Date]]("CaseCustomFieldValue", "dateValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Int]]("CaseTemplateCustomFieldValue", "order", usedOnlyByThisModel = false)
      .removeProperty[Option[String]]("CaseTemplateCustomFieldValue", "stringValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Boolean]]("CaseTemplateCustomFieldValue", "booleanValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Int]]("CaseTemplateCustomFieldValue", "integerValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Double]]("CaseTemplateCustomFieldValue", "floatValue", usedOnlyByThisModel = false)
      .removeProperty[Option[Date]]("CaseTemplateCustomFieldValue", "dateValue", usedOnlyByThisModel = false)
      .removeEdgeLabel("CaseCustomField")
      .removeEdgeLabel("AlertCustomField")
      .removeEdgeLabel("CaseTemplateCustomField")
}
