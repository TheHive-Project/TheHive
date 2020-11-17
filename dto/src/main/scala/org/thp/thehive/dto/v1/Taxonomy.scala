package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites, Writes}

case class InputTaxonomy (
  namespace: String,
  description: String,
  version: Int,
  predicates: Seq[String],
  values: Option[Seq[InputEntry]]
)

case class InputEntry(predicate: String, entry: Seq[InputValue])

case class InputValue(value: String, expanded: String, colour: Option[String])

object InputEntry {
  implicit val writes: Writes[InputEntry] = Json.writes[InputEntry]
}

object InputValue {
  implicit val writes: Writes[InputValue] = Json.writes[InputValue]
}

object InputTaxonomy {
  implicit val writes: OWrites[InputTaxonomy] = Json.writes[InputTaxonomy]
}

case class OutputTaxonomy(
  _id: String,
  _type: String,
  _createdBy: String,
  _updatedBy: Option[String] = None,
  _createdAt: Date,
  _updatedAt: Option[Date] = None,
  namespace: String,
  description: String,
  version: Int,
  enabled: Boolean,
  tags: Seq[OutputTag]
)

object OutputTaxonomy {
  implicit val format: OFormat[OutputTaxonomy] = Json.format[OutputTaxonomy]
}
