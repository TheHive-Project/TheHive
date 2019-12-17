package org.thp.thehive.migration.dto

import org.thp.thehive.models.Case

case class InputCase(
    `case`: Case,
    user: Option[String],
    organisations: Map[String, String],
    tags: Set[String],
    customFields: Map[String, Option[Any]],
    caseTemplate: Option[String],
    metaData: MetaData
)
