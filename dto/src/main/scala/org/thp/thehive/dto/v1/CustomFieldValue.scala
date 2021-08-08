package org.thp.thehive.dto.v1

import org.scalactic.Accumulation._
import org.scalactic.{Bad, Good, One}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers._
import org.thp.thehive.dto.{Description, String16, String64}
import play.api.libs.json._

import java.util.Date
import be.venneborg.refined.play.RefinedJsonFormats._

import scala.collection.Factory

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

case class InputCustomFieldValue(name: String64, value: JsValue, order: Option[Int])

object InputCustomFieldValue {

  val valueParser: FieldsParser[JsValue] = FieldsParser("customFieldValue") {
    case (_, FString(value))     => Good(JsString(value))
    case (_, FNumber(value))     => Good(JsNumber(value))
    case (_, FBoolean(value))    => Good(JsBoolean(value))
    case (_, FAny(value :: _))   => Good(JsString(value))
    case (_, FUndefined | FNull) => Good(JsNull)
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
      case InputCustomFieldValue(name, value, _) => name.value -> value
    }
    // TODO Change JsObject to JsArray ?
    JsObject(fields)
  }

  implicit val reads: Reads[Seq[InputCustomFieldValue]] = Reads[Seq[InputCustomFieldValue]] {
    case JsObject(fields) =>
      val out = fields.map {
        case (key, value) => InputCustomFieldValue(String64("customField.name", key), value, None)
      }.toSeq
      JsSuccess(out)
    case list: JsArray =>
      implicit val icfvReader: Reads[InputCustomFieldValue] = Reads[InputCustomFieldValue] { cf =>
        for {
          name  <- (cf \ "name").validate[String]
          value <- (cf \ "value").validate[JsValue]
          order <- (cf \ "order").validateOpt[Int]
        } yield InputCustomFieldValue(String64("customField.name", name), value, order)
      }
      Reads.traversableReads(implicitly[Factory[InputCustomFieldValue, Seq[InputCustomFieldValue]]], icfvReader).reads(list)
    case _ => JsError(Seq(JsPath -> Seq(JsonValidationError("error.expected.jsarray"))))
  }
}

case class OutputCustomFieldValue(_id: String, name: String, description: String, `type`: String, value: JsValue, order: Int)

object OutputCustomFieldValue {
  implicit val format: OFormat[OutputCustomFieldValue] = Json.format[OutputCustomFieldValue]
}
