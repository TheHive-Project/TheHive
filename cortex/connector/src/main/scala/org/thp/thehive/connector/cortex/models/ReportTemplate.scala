package org.thp.thehive.connector.cortex.models

import org.thp.scalligraph.VertexEntity

@VertexEntity
case class ReportTemplate(workerId: String, content: String)
