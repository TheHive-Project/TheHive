package org.thp.thehive.migration.dto

import org.thp.thehive.models.{Observable, ReportTag}

case class InputObservable(
    metaData: MetaData,
    observable: Observable,
    organisations: Set[String],
    dataOrAttachment: Either[String, InputAttachment],
    reportTags: List[ReportTag]
)