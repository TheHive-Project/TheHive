package connectors.misp

import java.util.Date

case class MispAlert(
  eventUuid: String,
  source: String,
  sourceRef: String,
  date: Date,
  lastSyncDate: Date,
  title: String,
  description: String,
  severity: Long,
  tags: Seq[String],
  tlp: Long,
  caseTemplate: String)

case class MispAttribute(
  id: String,
  tpe: String,
  category: String,
  uuid: String,
  eventId: Long,
  date: Date,
  comment: String,
  value: String,
  tags: Seq[String])