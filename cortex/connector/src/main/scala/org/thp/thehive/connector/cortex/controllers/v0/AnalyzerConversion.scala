package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.OutputCortexAnalyzer
import org.thp.thehive.connector.cortex.dto.v0.OutputAnalyzer

trait AnalyzerConversion {

  def toOutputAnalyzer(a: OutputCortexAnalyzer): OutputAnalyzer =
    a.into[OutputAnalyzer]
      .withFieldComputed(_.cortexIds, _.cortexIds.getOrElse(Nil))
      .transform
}
