package org.thp.thehive.migration.dto

import org.thp.scalligraph.EntityId
import org.thp.thehive.models.Audit

case class InputAudit(metaData: MetaData, audit: Audit) {
  def updateObjectId(objectId: Option[EntityId]): InputAudit = copy(audit = audit.copy(objectId = objectId.map(_.value)))
}
