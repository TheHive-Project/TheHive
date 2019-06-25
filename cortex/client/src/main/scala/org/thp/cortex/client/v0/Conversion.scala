package org.thp.cortex.client.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.client.models.{Artifact, OutputCortexAnalyzer}
import org.thp.cortex.dto.v0.{InputArtifact, OutputAnalyzer}

trait Conversion {

  def toOutputAnalyzer(a: OutputCortexAnalyzer): OutputAnalyzer =
    a.into[OutputAnalyzer]
      .withFieldComputed(_.cortexIds, _.cortexIds.getOrElse(Nil))
      .transform

  implicit class ArtifactConversion(a: Artifact) {
    def toInputArtifact: InputArtifact = a.into[InputArtifact].transform
  }
}
