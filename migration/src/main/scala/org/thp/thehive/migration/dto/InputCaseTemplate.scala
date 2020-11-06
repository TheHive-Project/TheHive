package org.thp.thehive.migration.dto

import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models.CaseTemplate

case class InputCaseTemplate(
    metaData: MetaData,
    caseTemplate: CaseTemplate,
    organisation: String,
    tags: Set[String],
    customFields: Seq[InputCustomFieldValue]
)
