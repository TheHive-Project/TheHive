package org.thp.thehive.models
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
case class Share()

@EdgeEntity[Share, Case]
case class ShareCase()

@EdgeEntity[Share, Organisation]
case class ShareOrganisation()

case class RichShare(caseId: String, organisationName: String)

object RichShare {
  def apply(`case`: Case with Entity, organisation: Organisation with Entity): RichShare =
    RichShare(`case`._id, organisation.name)
}
