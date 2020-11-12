package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json.{Json, OFormat, OWrites}

case class InputTaxonomy (
  namespace: String,
  description: String,
  version: Int,
  predicates: Seq[InputPredicate],
  values: Option[Seq[InputValue]]
)

case class InputPredicate(value: String, expanded: String)

case class InputValue(predicate: String, entry: Seq[InputPredicate])

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
  predicates: Seq[OutputPredicate],
  values: Option[Seq[OutputValue]]
)

case class OutputPredicate(value: String, expanded: String)

case class OutputValue(predicate: String, entry: Seq[OutputPredicate])

object OutputTaxonomy {
  implicit val format: OFormat[OutputTaxonomy] = Json.format[OutputTaxonomy]
}