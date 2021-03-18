package org.thp.thehive.migration.dto

import org.thp.thehive.models.Case

case class InputCase(
    `case`: Case,
    organisations: Map[String, String],
    customFields: Map[String, Option[Any]],
    metaData: MetaData
)
