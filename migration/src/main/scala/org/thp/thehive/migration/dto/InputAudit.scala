package org.thp.thehive.migration.dto

import org.thp.thehive.models.Audit

case class InputAudit(metaData: MetaData, audit: Audit) {
  def updateObjectId(objectId: Option[String]): InputAudit = copy(audit = audit.copy(objectId = objectId))
}
