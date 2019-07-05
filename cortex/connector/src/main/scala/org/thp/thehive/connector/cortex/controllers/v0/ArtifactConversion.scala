package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.CortexOutputArtifact
import org.thp.thehive.models.Observable

import scala.language.implicitConversions

object ArtifactConversion {

  implicit def fromCortexOutputArtifact(j: CortexOutputArtifact): Observable =
    j.into[Observable]
      .withFieldComputed(_.`type`, _.dataType)
      .withFieldComputed(_.message, _.message)
      .withFieldComputed(_.tlp, _.tlp)
      .withFieldComputed(_.tags, _.tags.toSet)
      .withFieldConst(_.ioc, false)
      .withFieldConst(_.sighted, false)
      .transform
}
