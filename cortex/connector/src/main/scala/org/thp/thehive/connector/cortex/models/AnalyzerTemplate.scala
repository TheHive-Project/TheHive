package org.thp.thehive.connector.cortex.models

import org.thp.scalligraph.VertexEntity

@VertexEntity
case class AnalyzerTemplate(workerId: String, content: String)
