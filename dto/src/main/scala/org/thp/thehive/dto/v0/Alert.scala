package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json._

import org.thp.scalligraph.controllers.WithParser

case class InputAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    externalLink: Option[String],
    title: String,
    description: String,
    severity: Option[Int] = None,
    date: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFields: Seq[InputCustomFieldValue] = Nil
)

object InputAlert {
  implicit val writes: OWrites[InputAlert] = Json.writes[InputAlert]
}

case class OutputSimilarCase(
    _id: String,
    id: String,
    caseId: Int, // number
    title: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    resolutionStatus: Option[String] = None,
    tags: Set[String] = Set.empty,
    tlp: Int,
    status: String,
    similarIOCCount: Int,
    iocCount: Int,
    similarArtifactCount: Int,
    artifactCount: Int
)

object OutputSimilarCase {
  implicit val format: OFormat[OutputSimilarCase] = Json.format[OutputSimilarCase]
}

case class OutputAlert(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
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
    status: String,
    follow: Boolean,
    `case`: Option[String],
    customFields: JsObject,
    caseTemplate: Option[String] = None,
    artifacts: Seq[OutputObservable] = Nil,
    similarCases: Seq[OutputSimilarCase]
)

object OutputAlert {
  implicit val reads: Reads[OutputAlert] = Reads[OutputAlert] { json =>
    for {
      _id          <- (json \ "_id").validate[String]
      id           <- (json \ "id").validate[String]
      createdBy    <- (json \ "createdBy").validate[String]
      updatedBy    <- (json \ "updatedBy").validateOpt[String]
      createdAt    <- (json \ "createdAt").validate[Date]
      updatedAt    <- (json \ "updatedAt").validateOpt[Date]
      _type        <- (json \ "_type").validate[String]
      tpe          <- (json \ "type").validate[String]
      source       <- (json \ "source").validate[String]
      sourceRef    <- (json \ "sourceRef").validate[String]
      externalLink <- (json \ "externalLink").validateOpt[String]
      case0        <- (json \ "case").validateOpt[String]
      title        <- (json \ "title").validate[String]
      description  <- (json \ "description").validate[String]
      severity     <- (json \ "severity").validate[Int]
      date         <- (json \ "date").validate[Date]
      tags         <- (json \ "tags").validate[Set[String]]
      tlp          <- (json \ "tlp").validate[Int]
      pap          <- (json \ "pap").validate[Int]
      status       <- (json \ "status").validate[String]
      follow       <- (json \ "follow").validate[Boolean]
      customFields <- (json \ "customFields").validate[JsObject]
      caseTemplate <- (json \ "caseTemplate").validateOpt[String]
      artifacts    <- (json \ "artifacts").validate[Seq[OutputObservable]]
      similarCases <- (json \ "similarCases").validate[Seq[OutputSimilarCase]]
    } yield OutputAlert(
      _id,
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      _type,
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
      status,
      follow,
      case0,
      customFields,
      caseTemplate,
      artifacts,
      similarCases
    )
  }
  implicit val writes: OWrites[OutputAlert] = OWrites[OutputAlert] { outputAlert =>
    Json.obj(
      "_id"          -> outputAlert._id,
      "id"           -> outputAlert.id,
      "createdBy"    -> outputAlert.createdBy,
      "updatedBy"    -> outputAlert.updatedBy,
      "createdAt"    -> outputAlert.createdAt,
      "updatedAt"    -> outputAlert.updatedAt,
      "_type"        -> outputAlert._type,
      "type"         -> outputAlert.`type`,
      "source"       -> outputAlert.source,
      "sourceRef"    -> outputAlert.sourceRef,
      "externalLink" -> outputAlert.externalLink,
      "case"         -> outputAlert.`case`,
      "title"        -> outputAlert.title,
      "description"  -> outputAlert.description,
      "severity"     -> outputAlert.severity,
      "date"         -> outputAlert.date,
      "tags"         -> outputAlert.tags,
      "tlp"          -> outputAlert.tlp,
      "pap"          -> outputAlert.pap,
      "status"       -> outputAlert.status,
      "follow"       -> outputAlert.follow,
      "customFields" -> outputAlert.customFields,
      "caseTemplate" -> outputAlert.caseTemplate,
      "artifacts"    -> outputAlert.artifacts
    )
  }
}
