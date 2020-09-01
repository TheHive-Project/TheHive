package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.UMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.cortex.models.{Action, ActionContext, AnalyzerTemplate, Job}
import org.thp.thehive.connector.cortex.services.ActionOps._
import org.thp.thehive.controllers.v0.Conversion.fromObjectType

@Singleton
class Properties @Inject() () {

  lazy val action: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Action]
      .property("responderId", UMapping.string)(_.field.readonly)
      .property("objectType", UMapping.string)(_.select(_.context.domainMap(o => fromObjectType(o._label))).readonly)
      .property("status", UMapping.string)(_.field.readonly)
      .property("startDate", UMapping.date)(_.field.readonly)
      .property("objectId", UMapping.id)(_.select(_.out[ActionContext]._id).readonly)
      .property("responderName", UMapping.string.optional)(_.field.readonly)
      .property("cortexId", UMapping.string.optional)(_.field.readonly)
      .property("tlp", UMapping.int.optional)(_.field.readonly)
      .build

  lazy val analyzerTemplate: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AnalyzerTemplate]
      .property("analyzerId", UMapping.string)(_.rename("workerId").readonly)
      .property("reportType", UMapping.string)(_.field.readonly)
      .property("content", UMapping.string)(_.field.updatable)
      .build

  val job: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Job]
      .property("analyzerId", UMapping.string)(_.rename("workerId").readonly)
      .property("cortexId", UMapping.string.optional)(_.field.readonly)
      .property("startDate", UMapping.date)(_.field.readonly)
      .property("status", UMapping.string)(_.field.readonly)
      .property("analyzerDefinition", UMapping.string)(_.rename("workerDefinition").readonly)
      .build
}
