package org.thp.thehive.dto.v0

import java.util.Date

import play.api.libs.json._

import org.scalactic.Accumulation._
import org.scalactic.{Bad, Every, Good, One, Or}
import org.thp.scalligraph.{AttributeError, InvalidFormatAttributeError}
import org.thp.scalligraph.controllers.{FNull, _}

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

case class InputCustomFieldValue(name: String, value: Option[Any])

object InputCustomFieldValue {

  def getStringCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("string") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None)))
      case FString(value) => Some(Good(InputCustomFieldValue(name, Some(value))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.string", "string", Set.empty, other))))
    }

  def getIntegerCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("integer") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None)))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(value.toLong))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.integer", "integer", Set.empty, other))))
    }

  def getFloatCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("float") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None)))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(value.toFloat))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.float", "float", Set.empty, other))))
    }

  def getDateCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("date") match {
      case FUndefined     => None
      case FNull          => Some(Good(InputCustomFieldValue(name, None)))
      case FNumber(value) => Some(Good(InputCustomFieldValue(name, Some(new Date(value.toLong)))))
      case other          => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.date", "date", Set.empty, other))))
    }

  def getBooleanCustomField(name: String, obj: FObject): Option[Or[InputCustomFieldValue, Every[AttributeError]]] =
    obj.get("boolean") match {
      case FUndefined      => None
      case FNull           => Some(Good(InputCustomFieldValue(name, None)))
      case FBoolean(value) => Some(Good(InputCustomFieldValue(name, Some(value))))
      case other           => Some(Bad(One(InvalidFormatAttributeError(s"customField.$name.boolean", "boolean", Set.empty, other))))
    }

  val parser: FieldsParser[Seq[InputCustomFieldValue]] = FieldsParser("customFieldValue") {
    case (_, FObject(fields)) =>
      fields
        .toSeq
        .validatedBy {
          case (name, FString(value))   => Good(InputCustomFieldValue(name, Some(value)))
          case (name, FNumber(value))   => Good(InputCustomFieldValue(name, Some(value)))
          case (name, FBoolean(value))  => Good(InputCustomFieldValue(name, Some(value)))
          case (name, FAny(value :: _)) => Good(InputCustomFieldValue(name, Some(value)))
          case (name, FNull)            => Good(InputCustomFieldValue(name, None))
          case (name, obj: FObject) =>
            getStringCustomField(name, obj) orElse
              getIntegerCustomField(name, obj) orElse
              getFloatCustomField(name, obj) orElse
              getDateCustomField(name, obj) orElse
              getBooleanCustomField(name, obj) getOrElse
              Good(InputCustomFieldValue(name, None))
          case (name, other) =>
            Bad(
              One(
                InvalidFormatAttributeError(name, "CustomFieldValue", Set("field: string", "field: number", "field: boolean", "field: string"), other)
              )
            )
        }
        .map(_.toSeq)
    case (_, FUndefined) => Good(Nil)
  }

  implicit val writes: Writes[Seq[InputCustomFieldValue]] = Writes[Seq[InputCustomFieldValue]] { icfv =>
    val fields = icfv.map {
      case InputCustomFieldValue(name, Some(s: String))  => name -> JsString(s)
      case InputCustomFieldValue(name, Some(l: Long))    => name -> JsNumber(l)
      case InputCustomFieldValue(name, Some(d: Double))  => name -> JsNumber(d)
      case InputCustomFieldValue(name, Some(i: Integer)) => name -> JsNumber(i.toLong)
      case InputCustomFieldValue(name, Some(f: Float))   => name -> JsNumber(f.toDouble)
      case InputCustomFieldValue(name, Some(b: Boolean)) => name -> JsBoolean(b)
      case InputCustomFieldValue(name, Some(d: Date))    => name -> JsNumber(d.getTime)
      case InputCustomFieldValue(name, None)             => name -> JsNull
      case InputCustomFieldValue(name, other)            => sys.error(s"The custom field $name has invalid value: $other (${other.getClass})")
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
