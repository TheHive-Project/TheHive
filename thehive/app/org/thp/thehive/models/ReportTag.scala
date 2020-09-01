package org.thp.thehive.models

import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}
import play.api.libs.json.JsValue

object ReportTagLevel extends Enumeration {
  val info, safe, suspicious, malicious = Value
}

@BuildEdgeEntity[Observable, ReportTag]
case class ObservableReportTag()

@BuildVertexEntity
case class ReportTag(origin: String, level: ReportTagLevel.Value, namespace: String, predicate: String, value: JsValue)
