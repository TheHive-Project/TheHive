package org.thp.thehive.dto.v1

import org.thp.thehive.dto.{Color, Description, String128}
import play.api.libs.json.Json.WithDefaultValues
import play.api.libs.json.{JsObject, Json, OFormat}

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

/*
Format based on :
https://tools.ietf.org/id/draft-dulaunoy-misp-taxonomy-format-04.html
 */

case class InputTaxonomy(
    namespace: String128,
    description: Description,
    version: Int,
    exclusive: Option[Boolean],
    predicates: Seq[InputPredicate],
    values: Seq[InputValue] = Nil
)

case class InputPredicate(
    value: String128,
    expanded: Option[Description],
    exclusive: Option[Boolean],
    description: Option[Description],
    colour: Option[Color]
)

case class InputValue(
    predicate: String128,
    entry: Seq[InputEntry]
)

case class InputEntry(
    value: String128,
    expanded: Option[Description],
    colour: Option[Color],
    description: Option[Description],
    numerical_value: Option[Int]
)

object InputTaxonomy {
  implicit val format: OFormat[InputTaxonomy] = Json.configured[WithDefaultValues].format[InputTaxonomy]
}

object InputPredicate {
  implicit val format: OFormat[InputPredicate] = Json.format[InputPredicate]
}

object InputValue {
  implicit val format: OFormat[InputValue] = Json.format[InputValue]
}

object InputEntry {
  implicit val format: OFormat[InputEntry] = Json.format[InputEntry]
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
    tags: Seq[OutputTag],
    extraData: JsObject
)

object OutputTaxonomy {
  implicit val format: OFormat[OutputTaxonomy] = Json.format[OutputTaxonomy]
}
