package connectors.misp

import java.util.Date

case class MispAlert(
  source: String,
  sourceRef: String,
  date: Date,
  lastSyncDate: Date,
  isPublished: Boolean,
  title: String,
  description: String,
  severity: Long,
  tags: Seq[String],
  tlp: Long,
  caseTemplate: String)

case class MispAttribute(
  id: String,
  category: String,
  tpe: String,
  date: Date,
  comment: String,
  value: String,
  tags: Seq[String])