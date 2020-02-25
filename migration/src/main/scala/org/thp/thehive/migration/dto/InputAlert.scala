package org.thp.thehive.migration.dto

import org.thp.thehive.models.Alert

case class InputAlert(
    metaData: MetaData,
    alert: Alert,
    caseId: Option[String],
    organisation: String,
    tags: Set[String],
    customFields: Map[String, Option[Any]],
    caseTemplate: Option[String]
) {
  def updateCaseId(caseId: Option[String]): InputAlert = copy(caseId = caseId)
}
