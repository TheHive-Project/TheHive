package org.thp.thehive.dto.v1

import java.util.Date

import org.scalactic.Accumulation.convertGenTraversableOnceToValidatable
import org.scalactic.{Bad, Good, One}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers.{FObject, FSeq, FieldsParser, WithParser}
import play.api.libs.json.{JsArray, JsObject, JsString, Json, OFormat, OWrites, Writes}

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
  implicit val parser: FieldsParser[InputEntry] = FieldsParser[InputEntry]

  implicit val writes: Writes[InputEntry] = Json.writes[InputEntry]
}

object InputValue {
  implicit val parser: FieldsParser[InputValue] = FieldsParser[InputValue]

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
  predicates: Seq[String],
  values: Seq[OutputEntry]
)

case class OutputEntry(predicate: String, entry: Seq[OutputValue])

case class OutputValue(value: String, expanded: String)

object OutputTaxonomy {
  implicit val format: OFormat[OutputTaxonomy] = Json.format[OutputTaxonomy]
}

object OutputEntry {
  implicit val format: OFormat[OutputEntry] = Json.format[OutputEntry]
}

object OutputValue {
  implicit val format: OFormat[OutputValue] = Json.format[OutputValue]
}