package org.thp.thehive.dto.v1

import java.util.Date

import org.scalactic.Accumulation._
import org.scalactic.{Bad, Good, One}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers._
import play.api.libs.json._

case class InputCustomField(name: String, description: String, `type`: String, mandatory: Option[Boolean])

object InputCustomField {
  implicit val writes: Writes[InputCustomField] = Json.writes[InputCustomField]
}

case class OutputCustomField(name: String, description: String, `type`: String, options: Seq[JsValue], mandatory: Boolean)

object OutputCustomField {
  implicit val format: OFormat[OutputCustomField] = Json.format[OutputCustomField]
}

case class InputCustomFieldValue(name: String, value: Option[Any], order: Option[Int])

object InputCustomFieldValue {

  val valueParser: FieldsParser[Option[Any]] = FieldsParser("customFieldValue") {
    case (_, FString(value))     => Good(Some(value))
    case (_, FNumber(value))     => Good(Some(value))
    case (_, FBoolean(value))    => Good(Some(value))
    case (_, FAny(value :: _))   => Good(Some(value))
    case (_, FUndefined | FNull) => Good(None)
  }

  val parser: FieldsParser[Seq[InputCustomFieldValue]] = FieldsParser("customFieldValues") {
    case (_, FObject(fields)) =>
      fields
        .toSeq
        .validatedBy {
          case (name, valueField) => valueParser(valueField).map(v => InputCustomFieldValue(name, v, None))
        }
        .map(_.toSeq)
    case (_, FSeq(list)) =>
      list.zipWithIndex.validatedBy {
        case (cf: FObject, i) =>
          val order = FieldsParser.int(cf.get("order")).getOrElse(i)
          for {
            name  <- FieldsParser.string(cf.get("name"))
            value <- valueParser(cf.get("value"))
          } yield InputCustomFieldValue(name, value, Some(order))
        case (other, i) =>
          Bad(
            One(
              InvalidFormatAttributeError(s"customFild[$i]", "CustomFieldValue", Set.empty, other)
            )
          )
      }
    case _ => Good(Nil)
  }
  implicit val writes: Writes[Seq[InputCustomFieldValue]] = Writes[Seq[InputCustomFieldValue]] { icfv =>
    val fields = icfv.map {
      case InputCustomFieldValue(name, Some(s: String), _)  => name -> JsString(s)
      case InputCustomFieldValue(name, Some(l: Long), _)    => name -> JsNumber(l)
      case InputCustomFieldValue(name, Some(d: Double), _)  => name -> JsNumber(d)
      case InputCustomFieldValue(name, Some(b: Boolean), _) => name -> JsBoolean(b)
      case InputCustomFieldValue(name, Some(d: Date), _)    => name -> JsNumber(d.getTime)
      case InputCustomFieldValue(name, None, _)             => name -> JsNull
      case InputCustomFieldValue(name, other, _)            => sys.error(s"The custom field $name has invalid value: $other (${other.getClass})")
    }
    JsObject(fields)
  }
}

case class OutputCustomFieldValue(_id: String, name: String, description: String, `type`: String, value: JsValue, order: Int)

object OutputCustomFieldValue {
  implicit val format: OFormat[OutputCustomFieldValue] = Json.format[OutputCustomFieldValue]
}
