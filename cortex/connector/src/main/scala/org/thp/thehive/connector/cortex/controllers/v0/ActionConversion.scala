package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.dto.v0.OutputAction
import org.thp.thehive.connector.cortex.models.{ActionContext, RichAction}
import org.thp.thehive.connector.cortex.services.ActionSteps

import scala.language.implicitConversions

object ActionConversion {

  implicit def toOutputAction(a: RichAction): Output[OutputAction] =
    Output[OutputAction](
      a.into[OutputAction]
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.objectId, _.context._id)
        .withFieldComputed(_.objectType, _.context._model.label)
        .withFieldComputed(_.operations, _.operations.getOrElse(""))
        .withFieldComputed(_.report, _.report.map(_.toString).getOrElse("{}"))
        .transform
    )

  val actionProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ActionSteps]
      .property("responderId", UniMapping.stringMapping)(_.simple.readonly)
      .property("objectType", UniMapping.stringMapping)(
        _.derived(
          _.outTo[ActionContext].value[String]("_label").map(_.toLowerCase)
        ).readonly
      )
      .property("status", UniMapping.stringMapping)(_.simple.readonly)
      .property("startDate", UniMapping.dateMapping)(_.simple.readonly)
      .property("objectId", UniMapping.stringMapping)(_.simple.readonly)
      .property("responderName", UniMapping.stringMapping.optional)(_.simple.readonly)
      .property("cortexId", UniMapping.stringMapping.optional)(_.simple.readonly)
      .property("tlp", UniMapping.intMapping.optional)(_.simple.readonly)
      .build
}
