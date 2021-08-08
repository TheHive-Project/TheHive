package org.thp.thehive.migration.dto

import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models.Case

case class InputCase(
    `case`: Case,
    organisations: Map[String, String],
    customFields: Seq[InputCustomFieldValue],
    metaData: MetaData
)
