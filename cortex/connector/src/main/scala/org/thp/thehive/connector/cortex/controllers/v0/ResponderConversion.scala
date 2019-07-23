package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.OutputCortexResponder
import org.thp.thehive.connector.cortex.dto.v0.OutputResponder

object ResponderConversion {

  def toOutputResponder(a: OutputCortexResponder): OutputResponder =
    a.into[OutputResponder]
      .withFieldComputed(_.cortexIds, _.cortexIds.getOrElse(Nil))
      .transform
}
