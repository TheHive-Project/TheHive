package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildVertexEntity
case class Pattern(
    patternId: String,
    name: String,
    description: Option[String],
    tactics: Set[String],
    url: String,
    patternType: String,
    capecId: Option[String],
    capecUrl: Option[String],
    revoked: Boolean,
    dataSources: Seq[String],
    defenseBypassed: Seq[String],
    detection: Option[String],
    permissionsRequired: Seq[String],
    platforms: Seq[String],
    remoteSupport: Boolean,
    systemRequirements: Seq[String],
    revision: Option[String]
)

@BuildEdgeEntity[Pattern, Pattern]
case class PatternPattern()

case class RichPattern(pattern: Pattern with Entity, parent: Option[Pattern with Entity]) {
  def patternId: String                = pattern.patternId
  def name: String                     = pattern.name
  def description: Option[String]      = pattern.description
  def tactics: Set[String]             = pattern.tactics
  def url: String                      = pattern.url
  def patternType: String              = pattern.patternType
  def capecId: Option[String]          = pattern.capecId
  def capecUrl: Option[String]         = pattern.capecUrl
  def revoked: Boolean                 = pattern.revoked
  def dataSources: Seq[String]         = pattern.dataSources
  def defenseBypassed: Seq[String]     = pattern.defenseBypassed
  def detection: Option[String]        = pattern.detection
  def permissionsRequired: Seq[String] = pattern.permissionsRequired
  def platforms: Seq[String]           = pattern.platforms
  def remoteSupport: Boolean           = pattern.remoteSupport
  def systemRequirements: Seq[String]  = pattern.systemRequirements
  def version: Option[String]          = pattern.revision
  def _id: EntityId                    = pattern._id
  def _createdAt: Date                 = pattern._createdAt
  def _createdBy: String               = pattern._createdBy
  def _updatedAt: Option[Date]         = pattern._updatedAt
  def _updatedBy: Option[String]       = pattern._updatedBy
}
