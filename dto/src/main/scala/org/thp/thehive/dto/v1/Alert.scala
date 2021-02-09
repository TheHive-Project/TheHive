package org.thp.thehive.dto.v1

import ai.x.play.json.Encoders.encoder
import ai.x.play.json.Jsonx
import org.thp.scalligraph.controllers.WithParser
import play.api.libs.json._

import java.util.Date

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
    customFields: Seq[OutputCustomFieldValue] = Seq.empty,
    caseTemplate: Option[String] = None,
    observableCount: Long,
    caseId: Option[String],
    extraData: JsObject
)

object OutputAlert {
  implicit val format: OFormat[OutputAlert] = Jsonx.formatCaseClass[OutputAlert]
}
