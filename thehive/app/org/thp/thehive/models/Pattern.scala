package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildVertexEntity
case class Pattern(
    patternId: String,
    name: String,
    description: Option[String],
    tactics: Seq[String],
    url: String,
    patternType: String,
    platforms: Seq[String],
    dataSources: Seq[String],
    version: Option[String]
)

@BuildEdgeEntity[Pattern, Pattern]
case class PatternPattern()

case class RichPattern(pattern: Pattern with Entity, parent: Option[Pattern with Entity]) {
  def patternId: String           = pattern.patternId
  def name: String                = pattern.name
  def description: Option[String] = pattern.description
  def tactics: Seq[String]        = pattern.tactics
  def url: String                 = pattern.url
  def patternType: String         = pattern.patternType
  def platforms: Seq[String]      = pattern.platforms
  def dataSources: Seq[String]    = pattern.dataSources
  def version: Option[String]     = pattern.version
  def _id: EntityId               = pattern._id
  def _createdAt: Date            = pattern._createdAt
  def _createdBy: String          = pattern._createdBy
  def _updatedAt: Option[Date]    = pattern._updatedAt
  def _updatedBy: Option[String]  = pattern._updatedBy
}
