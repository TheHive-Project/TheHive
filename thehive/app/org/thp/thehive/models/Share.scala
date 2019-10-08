package org.thp.thehive.models
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
case class Share()

@EdgeEntity[Share, Case]
case class ShareCase()

@EdgeEntity[Share, Observable]
case class ShareObservable()

@EdgeEntity[Share, Task]
case class ShareTask()

@EdgeEntity[Share, Profile]
case class ShareProfile()

case class RichShare(share: Share with Entity, caseId: String, organisationName: String, profileName: String)

//object RichShare {
//  def apply(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity): RichShare =
//    RichShare(`case`._id, organisation.name, profile.permissions)
//}
