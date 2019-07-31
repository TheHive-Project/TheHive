package org.thp.thehive.connector.cortex.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.thehive.connector.cortex.dto.v0.OutputWorker

object WorkerConversion {

  def toOutputWorker(a: (OutputCortexWorker, Seq[String])): OutputWorker =
    a._1
      .into[OutputWorker]
      .withFieldConst(_.cortexIds, a._2)
      .transform
}
