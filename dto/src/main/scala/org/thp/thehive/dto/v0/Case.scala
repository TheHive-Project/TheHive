package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json._

import org.thp.scalligraph.controllers.WithParser

case class InputCase(
    title: String,
    description: String,
    severity: Option[Int] = None,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    status: Option[String] = None,
    summary: Option[String] = None,
    user: Option[String] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil
)

object InputCase {
  implicit val writes: OWrites[InputCase] = Json.writes[InputCase]
}

case class OutputCase(
    _id: String,
    id: String,
    createdBy: String,
    updatedBy: Option[String] = None,
    createdAt: Date,
    updatedAt: Option[Date] = None,
    _type: String,
    caseId: Int, // number
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    impactStatus: Option[String] = None,
    resolutionStatus: Option[String] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    owner: Option[String], // user
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    stats: JsValue
)

object OutputCase {
  implicit val writes: OWrites[OutputCase] = OWrites[OutputCase](
    c =>
      Json.obj(
        "_id"              -> c._id,
        "id"               -> c.id,
        "createdBy"        -> c.createdBy,
        "updatedBy"        -> c.updatedBy,
        "createdAt"        -> c.createdAt,
        "updatedAt"        -> c.updatedAt,
        "_type"            -> c._type,
        "caseId"           -> c.caseId,
        "title"            -> c.title,
        "description"      -> c.description,
        "severity"         -> c.severity,
        "startDate"        -> c.startDate,
        "endDate"          -> c.endDate,
        "impactStatus"     -> c.impactStatus,
        "resolutionStatus" -> c.resolutionStatus,
        "tags"             -> c.tags,
        "flag"             -> c.flag,
        "tlp"              -> c.tlp,
        "pap"              -> c.pap,
        "status"           -> c.status,
        "summary"          -> c.summary,
        "owner"            -> c.owner,
        "customFields"     -> c.customFields,
        "stats"            -> c.stats
      )
  )

  implicit val reads: Reads[OutputCase] = Reads[OutputCase](
    j =>
      for {
        _id              <- (j \ "_id").validate[String]
        id               <- (j \ "id").validate[String]
        createdBy        <- (j \ "createdBy").validate[String]
        updatedBy        <- (j \ "updatedBy").validateOpt[String]
        createdAt        <- (j \ "createdAt").validate[Date]
        updatedAt        <- (j \ "updatedAt").validateOpt[Date]
        _type            <- (j \ "_type").validate[String]
        caseId           <- (j \ "caseId").validate[Int]
        title            <- (j \ "title").validate[String]
        description      <- (j \ "description").validate[String]
        severity         <- (j \ "severity").validate[Int]
        startDate        <- (j \ "startDate").validate[Date]
        endDate          <- (j \ "endDate").validateOpt[Date]
        impactStatus     <- (j \ "impactStatus").validateOpt[String]
        resolutionStatus <- (j \ "resolutionStatus").validateOpt[String]
        tags             <- (j \ "tags").validate[Set[String]]
        flag             <- (j \ "flag").validate[Boolean]
        tlp              <- (j \ "tlp").validate[Int]
        pap              <- (j \ "pap").validate[Int]
        status           <- (j \ "status").validate[String]
        summary          <- (j \ "summary").validateOpt[String]
        owner            <- (j \ "owner").validateOpt[String]
        customFields     <- (j \ "customFields").validate[Set[OutputCustomFieldValue]]
        stats            <- (j \ "stats").validate[JsValue]
      } yield OutputCase(
        _id,
        id,
        createdBy,
        updatedBy,
        createdAt,
        updatedAt,
        _type,
        caseId,
        title,
        description,
        severity,
        startDate,
        endDate,
        impactStatus,
        resolutionStatus,
        tags,
        flag,
        tlp,
        pap,
        status,
        summary,
        owner,
        customFields,
        stats
      )
  )
  implicit val format: OFormat[OutputCase] = OFormat(reads, writes)
}
