package org.thp.thehive.migration.dto

import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models.Alert

case class InputAlert(
    metaData: MetaData,
    alert: Alert,
    caseId: Option[String],
    organisation: String,
    customFields: Seq[InputCustomFieldValue],
    caseTemplate: Option[String]
) {
  def updateCaseId(caseId: Option[String]): InputAlert = copy(caseId = caseId)
}
