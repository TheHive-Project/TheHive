package org.thp.thehive.connector.cortex.models

import org.thp.scalligraph.VertexEntity
import org.thp.thehive.connector.cortex.models.ReportType.ReportType

object ReportType extends Enumeration {
  type ReportType = Value
  val short, long = Value
}

@VertexEntity
case class ReportTemplate(workerId: String, content: String, reportType: ReportType)
