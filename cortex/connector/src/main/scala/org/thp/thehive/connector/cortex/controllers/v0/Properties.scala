package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.cortex.services.{ActionSteps, JobSteps, ReportTemplateSteps}

@Singleton
class Properties @Inject()() {

  lazy val action: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ActionSteps]
      .property("responderId", UniMapping.string)(_.field.readonly)
      .property("objectType", UniMapping.string)(_.select(_.context.map(_._model.label)).readonly) // FIXME convert label to v0 object type
      .property("status", UniMapping.string)(_.field.readonly)
      .property("startDate", UniMapping.date)(_.field.readonly)
      .property("objectId", UniMapping.string)(_.field.readonly)
      .property("responderName", UniMapping.string.optional)(_.field.readonly)
      .property("cortexId", UniMapping.string.optional)(_.field.readonly)
      .property("tlp", UniMapping.int.optional)(_.field.readonly)
      .build

  lazy val reportTemplate: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ReportTemplateSteps]
      .property("analyzerId", UniMapping.string)(_.rename("workerId").readonly)
      .property("reportType", UniMapping.string)(_.field.readonly)
      .property("content", UniMapping.string)(_.field.updatable)
      .build

  val job: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[JobSteps]
      .property("analyzerId", UniMapping.string)(_.rename("workerId").readonly)
      .property("cortexId", UniMapping.string.optional)(_.field.readonly)
      .property("startDate", UniMapping.date)(_.field.readonly)
      .property("status", UniMapping.string)(_.field.readonly)
      .build
}
