package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.connector.cortex.dto.v0.OutputAction
import org.thp.thehive.connector.cortex.models.RichAction

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
}
