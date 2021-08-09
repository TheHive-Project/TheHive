package org.thp.thehive.dto.v0

import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.{AttributeError, InvalidFormatAttributeError}
import org.thp.thehive.dto.{Description, String16, String64}
import play.api.libs.json._
import be.venneborg.refined.play.RefinedJsonFormats._

case class OutputCustomField(
    id: String,
    name: String,
    reference: String,
    description: String,
    `type`: String,
    options: Seq[JsValue],
    mandatory: Boolean
)

object OutputCustomField {
  implicit val format: OFormat[OutputCustomField] = Json.format[OutputCustomField]
}

case class InputCustomFieldValue(name: String64, value: JsValue, order: Option[Int])

object InputCustomFieldValue {

  def getStringCustomField(name: String64, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("string") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, JsNull, obj.getNumber("order").map(_.toInt))))
      case FString(value) => Some(Good(InputCustomFieldValue(name, JsString(value), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.string", "string", Set.empty, other))))
    }

  def getIntegerCustomField(name: String64, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("integer") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, JsNull, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, JsNumber(value), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.integer", "integer", Set.empty, other))))
    }

  def getFloatCustomField(name: String64, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("float") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, JsNull, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, JsNumber(value), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.float", "float", Set.empty, other))))
    }

  def getDateCustomField(name: String64, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("date") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, JsNull, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, JsNumber(value), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.date", "date", Set.empty, other))))
    }

  def getBooleanCustomField(name: String64, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("boolean") match {
      case FUndefined      => None
      case FNull           => Some(Good(InputCustomFieldValue(name, JsNull, obj.getNumber("order").map(_.toInt))))
      case FBoolean(value) => Some(Good(InputCustomFieldValue(name, JsBoolean(value), obj.getNumber("order").map(_.toInt))))
      case other           => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.boolean", "boolean", Set.empty, other))))
    }

  val parser: FieldsParser[Seq[InputCustomFieldValue]] = FieldsParser("customFieldValue") {
    case (_, FObject(fields)) =>
      fields
        .toSeq
        .validatedBy {
          case (name, FString(value))   => Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsString(value), None))
          case (name, FNumber(value))   => Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsNumber(value), None))
          case (name, FBoolean(value))  => Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsBoolean(value), None))
          case (name, FAny(value :: _)) => Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsString(value), None))
          case (name, FNull)            => Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsNull, None))
          case (name, obj: FObject) =>
            getStringCustomField(String64("customFieldValue.name", name), obj) orElse
              getIntegerCustomField(String64("customFieldValue.name", name), obj) orElse
              getFloatCustomField(String64("customFieldValue.name", name), obj) orElse
              getDateCustomField(String64("customFieldValue.name", name), obj) orElse
              getBooleanCustomField(String64("customFieldValue.name", name), obj) getOrElse
              Good(InputCustomFieldValue(String64("customFieldValue.name", name), JsNull, None))
          case (name, other) =>
            Bad(
              One(
                InvalidFormatAttributeError(name, "CustomFieldValue", Set("field: string", "field: number", "field: boolean", "field: date"), other)
              )
            )
        }
        .map(_.toSeq)
    case (_, FUndefined) => Good(Nil)
  }

  implicit val writes: Writes[Seq[InputCustomFieldValue]] = Writes[Seq[InputCustomFieldValue]] { icfv =>
    val fields: Seq[(String, JsValue)] = icfv.map {
      case InputCustomFieldValue(name, value, _) => name.value -> value
    }
    JsObject(fields)
  }
}

case class InputCustomField(
    name: String64,
    description: Description,
    `type`: String16,
    reference: String64,
    mandatory: Option[Boolean],
    options: Seq[JsValue] = Nil
)

object InputCustomField {
  implicit val reads: Reads[InputCustomField] = Json.reads[InputCustomField]
}

case class OutputCustomFieldValue(name: String, description: String, tpe: String, value: Option[String])

object OutputCustomFieldValue {
  implicit val format: OFormat[OutputCustomFieldValue] = Json.format[OutputCustomFieldValue]
}
