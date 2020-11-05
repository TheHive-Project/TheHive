package org.thp.thehive.dto.v0

import java.util.Date

import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.scalligraph.controllers.{FNull, _}
import org.thp.scalligraph.{AttributeError, InvalidFormatAttributeError}
import play.api.libs.json._

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

case class InputCustomFieldValue(name: String, value: Option[Any], order: Option[Int])

object InputCustomFieldValue {

  def getStringCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("string") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None, obj.getNumber("order").map(_.toInt))))
      case FString(value) => Some(Good(InputCustomFieldValue(name, Some(value), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.string", "string", Set.empty, other))))
    }

  def getIntegerCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("integer") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(value.toInt), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.integer", "integer", Set.empty, other))))
    }

  def getFloatCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("float") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(value.toDouble), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.float", "float", Set.empty, other))))
    }

  def getDateCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("date") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None, obj.getNumber("order").map(_.toInt))))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(new Date(value.toLong)), obj.getNumber("order").map(_.toInt))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.date", "date", Set.empty, other))))
    }

  def getBooleanCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("boolean") match {
      case FUndefined      => None
      case FNull           => Some(Good(InputCustomFieldValue(name, None, obj.getNumber("order").map(_.toInt))))
      case FBoolean(value) => Some(Good(InputCustomFieldValue(name, Some(value), obj.getNumber("order").map(_.toInt))))
      case other           => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.boolean", "boolean", Set.empty, other))))
    }

  val parser: FieldsParser[Seq[InputCustomFieldValue]] = FieldsParser("customFieldValue") {
    case (_, FObject(fields)) =>
      fields
        .toSeq
        .zipWithIndex
        .validatedBy {
          case ((name, FString(value)), i)   => Good(InputCustomFieldValue(name, Some(value), Some(i)))
          case ((name, FNumber(value)), i)   => Good(InputCustomFieldValue(name, Some(value), Some(i)))
          case ((name, FBoolean(value)), i)  => Good(InputCustomFieldValue(name, Some(value), Some(i)))
          case ((name, FAny(value :: _)), i) => Good(InputCustomFieldValue(name, Some(value), Some(i)))
          case ((name, FNull), i)            => Good(InputCustomFieldValue(name, None, Some(i)))
          case ((name, obj: FObject), i) =>
            getStringCustomField(name, obj) orElse
              getIntegerCustomField(name, obj) orElse
              getFloatCustomField(name, obj) orElse
              getDateCustomField(name, obj) orElse
              getBooleanCustomField(name, obj) getOrElse
              Good(InputCustomFieldValue(name, None, None))
          case ((name, other), i) =>
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
    val fields = icfv.map {
      case InputCustomFieldValue(name, Some(s: String), _)  => name -> JsString(s)
      case InputCustomFieldValue(name, Some(l: Long), _)    => name -> JsNumber(l)
      case InputCustomFieldValue(name, Some(d: Double), _)  => name -> JsNumber(d)
      case InputCustomFieldValue(name, Some(i: Integer), _) => name -> JsNumber(i.toLong)
      case InputCustomFieldValue(name, Some(f: Float), _)   => name -> JsNumber(f.toDouble)
      case InputCustomFieldValue(name, Some(b: Boolean), _) => name -> JsBoolean(b)
      case InputCustomFieldValue(name, Some(d: Date), _)    => name -> JsNumber(d.getTime)
      case InputCustomFieldValue(name, None, _)             => name -> JsNull
      case InputCustomFieldValue(name, other, _)            => sys.error(s"The custom field $name has invalid value: $other (${other.getClass})")
    }
    JsObject(fields)
  }
}

case class InputCustomField(
    name: String,
    description: String,
    `type`: String,
    reference: String,
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
