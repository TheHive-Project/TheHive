package org.thp.thehive.connector.cortex.controllers.v0

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.connector.cortex.dto.v0.OutputAction
import org.thp.thehive.connector.cortex.models.{JobStatus, RichAction}
import org.thp.thehive.connector.cortex.services.ActionSteps

import scala.language.implicitConversions

object ActionConversion {

  implicit def toOutputAction(a: RichAction): Output[OutputAction] =
    Output[OutputAction](
      a.into[OutputAction]
        .withFieldComputed(_.status, _.status.toString)
        .withFieldComputed(_.objectId, _.context._id)
        .withFieldComputed(_.objectType, _.context._model.label)
        .transform
    )

  val actionProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ActionSteps]
      .property[String]("responderId")(_.simple.readonly)
      .property[String]("objectType")(_.simple.readonly)
      .property[String]("status")(_.simple.readonly)
      .property[Date]("startDate")(_.simple.readonly)
      .property[String]("objectId")(_.simple.readonly)
      .property[Option[String]]("responderName")(_.simple.readonly)
      .property[Option[String]]("cortexId")(_.simple.readonly)
      .property[Option[Int]]("tlp")(_.simple.readonly)
      .build
}
