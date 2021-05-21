package org.thp.thehive.dto.v1

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx
import org.thp.scalligraph.controllers.WithParser
import org.thp.thehive.dto.{Description, Pap, Severity, String128, String16, String512, Tlp}
import play.api.libs.json._

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputAlert(
    `type`: String16,
    source: String16,
    sourceRef: String128,
    externalLink: Option[String512],
    title: String512,
    description: Description,
    severity: Option[Severity] = None,
    date: Date = new Date,
    tags: Set[String128] = Set.empty,
    flag: Option[Boolean] = None,
    tlp: Option[Tlp] = None,
    pap: Option[Pap] = None,
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
    customFields: Seq[OutputCustomFieldValue] = Seq.empty,
    caseTemplate: Option[String] = None,
    observableCount: Long,
    caseId: Option[String],
    extraData: JsObject
)

object OutputAlert {
  implicit val format: OFormat[OutputAlert] = Jsonx.formatCaseClass[OutputAlert]
}
