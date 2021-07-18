package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}
import org.thp.thehive.services.SharingProfile

import java.util.Date

@BuildVertexEntity
case class Share(owner: Boolean, taskRule: String, observableRule: String)
/*
rule = { none, all, existingOnly, upcomingOnly }
 */
object Share {
  def fromProfile(owner: Boolean, shareConfig: SharingProfile): Share =
    Share(owner, shareConfig.taskRule, shareConfig.observableRule)
}

@BuildEdgeEntity[Share, Case]
case class ShareCase()

@BuildEdgeEntity[Share, Observable]
case class ShareObservable()

@BuildEdgeEntity[Share, Task]
case class ShareTask(actionRequired: Boolean = false)

@BuildEdgeEntity[Share, Profile]
case class ShareProfile()

case class RichShare(share: Share with Entity, caseId: EntityId, organisationName: String, profileName: String) {
  def _id: EntityId              = share._id
  def _createdBy: String         = share._createdBy
  def _updatedBy: Option[String] = share._updatedBy
  def _createdAt: Date           = share._createdAt
  def _updatedAt: Option[Date]   = share._updatedAt
  def owner: Boolean             = share.owner
}
