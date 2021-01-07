package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildVertexEntity
case class Technique(
    techniqueId: String,
    name: String,
    description: Option[String],
    tactics: Seq[String],
    url: String,
    techniqueType: String,
    platforms: Seq[String],
    dataSources: Seq[String],
    // TODO capec id
    version: Option[String]
)

@BuildEdgeEntity[Technique, Technique]
case class TechniqueTechnique()

case class RichTechnique(technique: Technique with Entity, parent: Option[Technique with Entity]) {
  def techniqueId: String         = technique.techniqueId
  def name: String                = technique.name
  def description: Option[String] = technique.description
  def tactics: Seq[String]        = technique.tactics
  def url: String                 = technique.url
  def techniqueType: String       = technique.techniqueType
  def platforms: Seq[String]      = technique.platforms
  def dataSources: Seq[String]    = technique.dataSources
  def version: Option[String]     = technique.version
  def _id: EntityId               = technique._id
  def _createdAt: Date            = technique._createdAt
  def _createdBy: String          = technique._createdBy
  def _updatedAt: Option[Date]    = technique._updatedAt
  def _updatedBy: Option[String]  = technique._updatedBy
}
