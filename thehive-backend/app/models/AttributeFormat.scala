package models

import play.api.libs.json.{JsNumber, JsValue}

import org.scalactic.{Every, Good, One, Or}

import org.elastic4play.controllers.{InputValue, JsonInputValue, StringInputValue}
import org.elastic4play.models.{Attribute, AttributeDefinition, NumberAttributeFormat}
import org.elastic4play.services.DBLists
import org.elastic4play.{AttributeError, InvalidFormatAttributeError}

object SeverityAttributeFormat extends NumberAttributeFormat {

  def isValidValue(value: Long): Boolean = 1 <= value && value <= 4

  override def definition(dblists: DBLists, attribute: Attribute[Long]): Seq[AttributeDefinition] =
    Seq(
      AttributeDefinition(
        attribute.attributeName,
        name,
        attribute.description,
        Seq(JsNumber(1), JsNumber(2), JsNumber(3)),
        Seq("low", "medium", "high")
      )
    )

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] =
    value match {
      case JsNumber(v) if subNames.isEmpty && isValidValue(v.toLong) ⇒ Good(value)
      case _                                                         ⇒ formatError(JsonInputValue(value))
    }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Long Or Every[AttributeError] =
    value match {
      case StringInputValue(Seq(v)) if subNames.isEmpty ⇒
        try {
          val longValue = v.toLong
          if (isValidValue(longValue)) Good(longValue)
          else formatError(value)
        } catch {
          case _: Throwable ⇒ formatError(value)
        }
      case JsonInputValue(JsNumber(v)) ⇒ Good(v.longValue)
      case _                           ⇒ formatError(value)
    }
}

object TlpAttributeFormat extends NumberAttributeFormat {

  def isValidValue(value: Long): Boolean = 0 <= value && value <= 3

  override def definition(dblists: DBLists, attribute: Attribute[Long]): Seq[AttributeDefinition] =
    Seq(
      AttributeDefinition(
        attribute.attributeName,
        name,
        attribute.description,
        Seq(JsNumber(0), JsNumber(1), JsNumber(2), JsNumber(3)),
        Seq("white", "green", "amber", "red")
      )
    )

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case JsNumber(v) if subNames.isEmpty && isValidValue(v.toLong) ⇒ Good(value)
    case _                                                         ⇒ formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Long Or Every[AttributeError] =
    value match {
      case StringInputValue(Seq(v)) if subNames.isEmpty ⇒
        try {
          val longValue = v.toLong
          if (isValidValue(longValue)) Good(longValue)
          else formatError(value)
        } catch {
          case _: Throwable ⇒ formatError(value)
        }
      case JsonInputValue(JsNumber(v)) ⇒ Good(v.longValue)
      case _                           ⇒ formatError(value)
    }
}
