package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}

@BuildVertexEntity
case class Share(owner: Boolean)

@BuildEdgeEntity[Share, Case]
case class ShareCase()

@BuildEdgeEntity[Share, Observable]
case class ShareObservable()

@BuildEdgeEntity[Share, Task]
case class ShareTask()

@BuildEdgeEntity[Share, Profile]
case class ShareProfile()

case class RichShare(share: Share with Entity, caseId: String, organisationName: String, profileName: String) {
  def _id: String                = share._id
  def _createdBy: String         = share._createdBy
  def _updatedBy: Option[String] = share._updatedBy
  def _createdAt: Date           = share._createdAt
  def _updatedAt: Option[Date]   = share._updatedAt
  def owner: Boolean             = share.owner
}

//object RichShare {
//  def apply(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity): RichShare =
//    RichShare(`case`._id, organisation.name, profile.permissions)
//}
