package org.thp.thehive.dto.v1

import org.scalactic.Accumulation._
import org.scalactic.{Bad, Good, One}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers._
import org.thp.thehive.dto.{Description, String16, String64}
import play.api.libs.json._

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

case class InputCustomField(
    name: String64,
    displayName: Option[String64],
    description: Description,
    `type`: String16,
    mandatory: Option[Boolean],
    options: Seq[JsValue] = Nil
)

object InputCustomField {
  implicit val writes: Writes[InputCustomField] = Json.writes[InputCustomField]
}

case class OutputCustomField(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    displayName: String,
    description: String,
    `type`: String,
    options: Seq[JsValue],
    mandatory: Boolean
)

object OutputCustomField {
  implicit val format: OFormat[OutputCustomField] = Json.format[OutputCustomField]
}

case class InputCustomFieldValue(name: String64, value: Option[Any], order: Option[Int])

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
          case (name, valueField) => valueParser(valueField).map(v => InputCustomFieldValue(String64("customField.name", name), v, None))
        }
        .map(_.toSeq)
    case (_, FSeq(list)) =>
      list
        .validatedBy {
          case cf: FObject =>
            val order = FieldsParser.int(cf.get("order")).toOption
            for {
              name  <- FieldsParser.string(cf.get("name"))
              value <- valueParser(cf.get("value"))
            } yield InputCustomFieldValue(String64("customField.name", name), value, order)
          case other =>
            Bad(
              One(
                InvalidFormatAttributeError(s"customField", "CustomFieldValue", Set.empty, other)
              )
            )
        }
    case _ => Good(Nil)
  }

  implicit val writes: Writes[Seq[InputCustomFieldValue]] = Writes[Seq[InputCustomFieldValue]] { icfv =>
    val fields = icfv.map {
      case InputCustomFieldValue(name, Some(s: String), _)  => name.value -> JsString(s)
      case InputCustomFieldValue(name, Some(l: Long), _)    => name.value -> JsNumber(l)
      case InputCustomFieldValue(name, Some(d: Double), _)  => name.value -> JsNumber(d)
      case InputCustomFieldValue(name, Some(b: Boolean), _) => name.value -> JsBoolean(b)
      case InputCustomFieldValue(name, Some(d: Date), _)    => name.value -> JsNumber(d.getTime)
      case InputCustomFieldValue(name, None, _)             => name.value -> JsNull
      case InputCustomFieldValue(name, other, _)            => sys.error(s"The custom field $name has invalid value: $other (${other.getClass})")
    }
    // TODO Change JsObject to JsArray ?
    JsObject(fields)
  }

  private def valueReader(jsValue: JsValue): Option[Any] = jsValue match {
    case n: JsNumber => Some(n)
    case s: JsString => Some(s)
    case b: JsBoolean => Some(b)
    case _ => None
  }

  implicit val reads: Reads[Seq[InputCustomFieldValue]] = Reads[Seq[InputCustomFieldValue]] {
    case JsObject(fields) =>
      val out = fields.map {
        case (key, value) => InputCustomFieldValue(String64("customField.name", key), valueReader(value), None)
      }.toSeq
      JsSuccess(out)
    case list: JsArray =>
      implicit val icfvReader: Reads[InputCustomFieldValue] = Reads[InputCustomFieldValue] { cf =>
        for {
          name <- (cf \ "name").validate[String]
          v <- (cf \ "value").validate[JsValue]
          value = valueReader(v)
          order <- (cf \ "order").validateOpt[Int]
        } yield InputCustomFieldValue(String64("customField.name", name), value, order)
      }
      list.validate[Seq[InputCustomFieldValue]]
  }
}

case class OutputCustomFieldValue(_id: String, name: String, description: String, `type`: String, value: JsValue, order: Int)

object OutputCustomFieldValue {
  implicit val format: OFormat[OutputCustomFieldValue] = Json.format[OutputCustomFieldValue]
}
