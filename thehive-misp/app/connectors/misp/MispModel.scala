package connectors.misp

import java.util.Date

import models.Artifact

import org.elastic4play.services.Attachment

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

case class ExportedMispAttribute(
  artifact: Artifact,
  tpe: String,
  category: String,
  value: Either[String, Attachment],
  comment: Option[String])

case class MispExportError(message: String, artifact: Artifact) extends Exception(message)