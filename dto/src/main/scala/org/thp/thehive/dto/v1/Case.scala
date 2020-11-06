package org.thp.thehive.dto.v1

import java.util.Date

import org.thp.scalligraph.controllers.WithParser
import play.api.libs.json._

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
    customFieldValues: Seq[InputCustomFieldValue] = Nil
)

object InputCase {
  implicit val writes: OWrites[InputCase] = Json.writes[InputCase]
}

case class OutputCase(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    number: Int,
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    impactStatus: Option[String] = None,
    resolutionStatus: Option[String] = None,
    assignee: Option[String],
    customFields: Seq[OutputCustomFieldValue] = Seq.empty,
    extraData: JsObject
)

object OutputCase {

  val reads: Reads[OutputCase] = Reads[OutputCase] { json =>
    for {
      _id              <- (json \ "_id").validate[String]
      _type            <- (json \ "_type").validate[String]
      _createdBy       <- (json \ "_createdBy").validate[String]
      _updatedBy       <- (json \ "_updatedBy").validateOpt[String]
      _createdAt       <- (json \ "_createdAt").validate[Date]
      _updatedAt       <- (json \ "_updatedAt").validateOpt[Date]
      number           <- (json \ "number").validate[Int]
      title            <- (json \ "title").validate[String]
      description      <- (json \ "description").validate[String]
      severity         <- (json \ "severity").validate[Int]
      startDate        <- (json \ "startDate").validate[Date]
      endDate          <- (json \ "endDate").validateOpt[Date]
      tags             <- (json \ "tags").validate[Set[String]]
      flag             <- (json \ "flag").validate[Boolean]
      tlp              <- (json \ "tlp").validate[Int]
      pap              <- (json \ "pap").validate[Int]
      status           <- (json \ "status").validate[String]
      summary          <- (json \ "summary").validateOpt[String]
      impactStatus     <- (json \ "impactStatus").validateOpt[String]
      resolutionStatus <- (json \ "resolutionStatus").validateOpt[String]
      assignee         <- (json \ "assignee").validateOpt[String]
      customFields     <- (json \ "customFields").validate[Seq[OutputCustomFieldValue]]
      extraData        <- (json \ "extraData").validate[JsObject]
    } yield OutputCase(
      _id,
      _type,
      _createdBy,
      _updatedBy,
      _createdAt,
      _updatedAt,
      number,
      title,
      description,
      severity,
      startDate,
      endDate,
      tags,
      flag,
      tlp,
      pap,
      status,
      summary,
      impactStatus,
      resolutionStatus,
      assignee,
      customFields,
      extraData
    )
  }

  val writes: OWrites[OutputCase] = OWrites[OutputCase] { outputCase =>
    Json.obj(
      "_id"              -> outputCase._id,
      "_type"            -> outputCase._type,
      "_createdBy"       -> outputCase._createdBy,
      "_updatedBy"       -> outputCase._updatedBy,
      "_createdAt"       -> outputCase._createdAt,
      "_updatedAt"       -> outputCase._updatedAt,
      "number"           -> outputCase.number,
      "title"            -> outputCase.title,
      "description"      -> outputCase.description,
      "severity"         -> outputCase.severity,
      "startDate"        -> outputCase.startDate,
      "endDate"          -> outputCase.endDate,
      "tags"             -> outputCase.tags,
      "flag"             -> outputCase.flag,
      "tlp"              -> outputCase.tlp,
      "pap"              -> outputCase.pap,
      "status"           -> outputCase.status,
      "summary"          -> outputCase.summary,
      "impactStatus"     -> outputCase.impactStatus,
      "resolutionStatus" -> outputCase.resolutionStatus,
      "assignee"         -> outputCase.assignee,
      "customFields"     -> outputCase.customFields,
      "extraData"        -> outputCase.extraData
    )
  }

  implicit val format: OFormat[OutputCase] = OFormat(reads, writes)
}
