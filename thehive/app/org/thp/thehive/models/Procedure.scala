package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}

import java.util.Date

@BuildVertexEntity
case class Procedure(
    description: Option[String],
    occurDate: Date,
    tactic: String
    // metadata
)

@BuildEdgeEntity[Procedure, Pattern]
case class ProcedurePattern()

case class RichProcedure(procedure: Procedure with Entity, pattern: Pattern with Entity) {
  def description: Option[String] = procedure.description
  def occurDate: Date             = procedure.occurDate
  def tactic: String              = procedure.tactic
  def _id: EntityId               = procedure._id
  def _createdAt: Date            = procedure._createdAt
  def _createdBy: String          = procedure._createdBy
  def _updatedAt: Option[Date]    = procedure._updatedAt
  def _updatedBy: Option[String]  = procedure._updatedBy

}
