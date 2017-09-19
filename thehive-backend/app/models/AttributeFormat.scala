package models

import play.api.libs.json.JsNumber

import org.elastic4play.models.{ Attribute, AttributeDefinition, NumberAttributeFormat }
import org.elastic4play.services.DBLists

object SeverityAttributeFormat extends NumberAttributeFormat {
  override def definition(dblists: DBLists, attribute: Attribute[Long]): Seq[AttributeDefinition] =
    Seq(AttributeDefinition(
      attribute.name,
      name,
      attribute.description,
      Seq(JsNumber(1), JsNumber(2), JsNumber(3)),
      Seq("low", "medium", "high")))
}

object TlpAttributeFormat extends NumberAttributeFormat {
  override def definition(dblists: DBLists, attribute: Attribute[Long]): Seq[AttributeDefinition] =
    Seq(AttributeDefinition(
      attribute.name,
      name,
      attribute.description,
      Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)),
      Seq("white", "green", "amber", "red")))
}