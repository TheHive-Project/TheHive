package connectors.misp

import java.util.Date

import models.Artifact

import org.elastic4play.services.Attachment
import org.elastic4play.utils.Hash

sealed trait ArtifactData
case class SimpleArtifactData(data: String) extends ArtifactData
case class AttachmentArtifact(attachment: Attachment) extends ArtifactData {
  def name: String = attachment.name
  def hashes: Seq[Hash] = attachment.hashes
  def size: Long = attachment.size
  def contentType: String = attachment.contentType
  def id: String = attachment.id
}
case class RemoteAttachmentArtifact(filename: String, reference: String, tpe: String) extends ArtifactData

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

case class MispArtifact(
  value: ArtifactData,
  dataType: String,
  message: String,
  tlp: Long,
  tags: Seq[String],
  startDate: Date)

case class MispExportError(message: String, artifact: Artifact) extends Exception(message)

