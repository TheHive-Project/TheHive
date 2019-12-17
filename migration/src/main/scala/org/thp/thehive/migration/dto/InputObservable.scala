package org.thp.thehive.migration.dto

import org.thp.thehive.models.Observable

case class InputObservable(
    metaData: MetaData,
    observable: Observable,
    organisations: Seq[String],
    `type`: String,
    tags: Set[String],
    dataOrAttachment: Either[String, InputAttachment]
)
