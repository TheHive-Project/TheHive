package org.thp.thehive.dto.v1

import java.util.Date

import play.api.libs.json._

import org.scalactic.Accumulation._
import org.scalactic.{Bad, Good, One}
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers._

case class InputCustomField(name: String, description: String, `type`: String)

object InputCustomField {
  implicit val writes: Writes[InputCustomField] = Json.writes[InputCustomField]
}

case class OutputCustomField(name: String, description: String, `type`: String)

object OutputCustomField {
  implicit val format: Format[OutputCustomField] = Json.format[OutputCustomField]
}

case class InputCustomFieldValue(name: String, value: Any)

object InputCustomFieldValue {
  val parser: FieldsParser[Seq[InputCustomFieldValue]] = FieldsParser("customFieldValue") {
    case (_, FObject(fields)) ⇒
      fields
        .validatedBy {
          case (name, FString(value))   ⇒ Good(InputCustomFieldValue(name, value))
          case (name, FNumber(value))   ⇒ Good(InputCustomFieldValue(name, value))
          case (name, FBoolean(value))  ⇒ Good(InputCustomFieldValue(name, value))
          case (name, FAny(value :: _)) ⇒ Good(InputCustomFieldValue(name, value))
          case (name, other) ⇒
            Bad(One(
              InvalidFormatAttributeError(name, "CustomFieldValue", Seq("field: string", "field: number", "field: boolean", "field: string"), other)))
        }
        .map(_.toSeq)
  }
  implicit val writes: Writes[Seq[InputCustomFieldValue]] = Writes[Seq[InputCustomFieldValue]] { icfv ⇒
    val fields = icfv.map {
      case InputCustomFieldValue(name, s: String)  ⇒ name → JsString(s)
      case InputCustomFieldValue(name, l: Long)    ⇒ name → JsNumber(l)
      case InputCustomFieldValue(name, d: Double)  ⇒ name → JsNumber(d)
      case InputCustomFieldValue(name, b: Boolean) ⇒ name → JsBoolean(b)
      case InputCustomFieldValue(name, d: Date)    ⇒ name → JsNumber(d.getTime)
      case InputCustomFieldValue(name, other)      ⇒ sys.error(s"The custom field $name has invalid value: $other (${other.getClass})")
    }
    JsObject(fields)
  }
}

case class OutputCustomFieldValue(name: String, description: String, tpe: String, value: String)

object OutputCustomFieldValue {
  implicit val format: Format[OutputCustomFieldValue] = Json.format[OutputCustomFieldValue]
}
