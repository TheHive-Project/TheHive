package org.thp.thehive.models

import play.api.libs.json.JsValue

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

object ReportTagLevel extends Enumeration {
  val info, safe, suspicious, malicious = Value
}

@EdgeEntity[Observable, ReportTag]
case class ObservableReportTag()

@VertexEntity
case class ReportTag(origin: String, level: ReportTagLevel.Value, namespace: String, predicate: String, value: JsValue)
