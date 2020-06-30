package org.thp.thehive.dto.v1

import java.util.Date

import org.thp.scalligraph.controllers.WithParser
import play.api.libs.json.{Json, OFormat, OWrites, Reads}

case class InputAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    externalLink: Option[String],
    title: String,
    description: String,
    severity: Option[Int] = None,
    date: Date = new Date,
    tags: Set[String] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil
)

object InputAlert {
  implicit val writes: OWrites[InputAlert] = Json.writes[InputAlert]
}

case class OutputAlert(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    `type`: String,
    source: String,
    sourceRef: String,
    externalLink: Option[String],
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String] = Set.empty,
    tlp: Int,
    pap: Int,
    read: Boolean,
    follow: Boolean,
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    caseTemplate: Option[String] = None,
    observableCount: Long,
    caseId: Option[String]
)

object OutputAlert {
  implicit val reads: Reads[OutputAlert] = Reads[OutputAlert] { json =>
    for {
      _id             <- (json \ "_id").validate[String]
      _type           <- (json \ "_type").validate[String]
      _createdBy      <- (json \ "_createdBy").validate[String]
      _updatedBy      <- (json \ "_updatedBy").validateOpt[String]
      _createdAt      <- (json \ "_createdAt").validate[Date]
      _updatedAt      <- (json \ "_updatedAt").validateOpt[Date]
      tpe             <- (json \ "type").validate[String]
      source          <- (json \ "source").validate[String]
      sourceRef       <- (json \ "sourceRef").validate[String]
      externalLink    <- (json \ "externalLink").validateOpt[String]
      title           <- (json \ "title").validate[String]
      description     <- (json \ "description").validate[String]
      severity        <- (json \ "severity").validate[Int]
      date            <- (json \ "date").validate[Date]
      tags            <- (json \ "tags").validate[Set[String]]
      tlp             <- (json \ "tlp").validate[Int]
      pap             <- (json \ "pap").validate[Int]
      read            <- (json \ "read").validate[Boolean]
      follow          <- (json \ "follow").validate[Boolean]
      customFields    <- (json \ "customFields").validate[Set[OutputCustomFieldValue]]
      caseTemplate    <- (json \ "caseTemplate").validateOpt[String]
      observableCount <- (json \ "observableCount").validate[Long]
      caseId          <- (json \ "caseId").validateOpt[String]
    } yield OutputAlert(
      _id,
      _type,
      _createdBy,
      _updatedBy,
      _createdAt,
      _updatedAt,
      tpe,
      source,
      sourceRef,
      externalLink,
      title,
      description,
      severity,
      date,
      tags,
      tlp,
      pap,
      read,
      follow,
      customFields,
      caseTemplate,
      observableCount,
      caseId
    )
  }
  implicit val writes: OWrites[OutputAlert] = OWrites[OutputAlert] { outputAlert =>
    Json.obj(
      "_id"             -> outputAlert._id,
      "_type"           -> outputAlert._type,
      "_createdBy"      -> outputAlert._createdBy,
      "_updatedBy"      -> outputAlert._updatedBy,
      "_createdAt"      -> outputAlert._createdAt,
      "_updatedAt"      -> outputAlert._updatedAt,
      "type"            -> outputAlert.`type`,
      "source"          -> outputAlert.source,
      "sourceRef"       -> outputAlert.sourceRef,
      "externalLink"    -> outputAlert.externalLink,
      "title"           -> outputAlert.title,
      "description"     -> outputAlert.description,
      "severity"        -> outputAlert.severity,
      "date"            -> outputAlert.date,
      "tags"            -> outputAlert.tags,
      "tlp"             -> outputAlert.tlp,
      "pap"             -> outputAlert.pap,
      "read"            -> outputAlert.read,
      "follow"          -> outputAlert.follow,
      "customFields"    -> outputAlert.customFields,
      "caseTemplate"    -> outputAlert.caseTemplate,
      "observableCount" -> outputAlert.observableCount,
      "caseId"          -> outputAlert.caseId
    )
  }

  implicit val format: OFormat[OutputAlert] = OFormat(reads, writes)
}
