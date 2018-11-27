package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

import org.thp.scalligraph.controllers.WithParser

case class InputAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Option[Int] = None,
    date: Date = new Date,
    tags: Seq[String] = Nil,
    flag: Option[Boolean] = None,
    tlp: Option[Int] = None,
    pap: Option[Int] = None,
    @WithParser(InputCustomFieldValue.parser)
    customFieldValue: Seq[InputCustomFieldValue] = Nil)

object InputAlert {
  implicit val writes: OWrites[InputAlert] = Json.writes[InputAlert]
}

case class OutputAlert(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    follow: Boolean,
    user: String,
    customFields: Set[OutputCustomFieldValue] = Set.empty)

object OutputAlert {
  implicit val format: OFormat[OutputAlert] = Json.format[OutputAlert]
}
