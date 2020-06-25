package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import play.api.libs.json.JsValue

object ReportTagLevel extends Enumeration {
  val info, safe, suspicious, malicious = Value
}

@EdgeEntity[Observable, ReportTag]
case class ObservableReportTag()

@VertexEntity
case class ReportTag(origin: String, level: ReportTagLevel.Value, namespace: String, predicate: String, value: JsValue)
