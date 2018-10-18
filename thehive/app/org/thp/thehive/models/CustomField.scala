package org.thp.thehive.models

import java.util.Date

import play.api.libs.json.{JsNumber, Writes}

import org.thp.scalligraph._

sealed abstract class CustomFieldType[T] {
  def setValue(value: Any): CaseCustomField =
    setTypedValue
      .lift(value)
      .getOrElse {
        throw BadRequestError(s"""Invalid value type for custom field.
             |  Expected: $name
             |  Found   : $value (${value.getClass})
          """.stripMargin)
      }
  protected val setTypedValue: PartialFunction[Any, CaseCustomField]
  def getValue(ccf: CaseCustomField): T
  val name: String
  val writes: Writes[T]
  override def toString: String = name
}

object CustomFieldString extends CustomFieldType[String] {
  override val setTypedValue: PartialFunction[Any, CaseCustomField] = { case s: String ⇒ CaseCustomField(stringValue = Some(s)) }
  override def getValue(ccf: CaseCustomField): String =
    ccf.stringValue.getOrElse(throw InternalError(s"CaseCustomField $ccf is string type but don't have string value"))
  override val name: String           = "string"
  override val writes: Writes[String] = Writes.StringWrites
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override val setTypedValue = { case b: Boolean ⇒ CaseCustomField(booleanValue = Some(b)) }
  override def getValue(ccf: CaseCustomField): Boolean =
    ccf.booleanValue.getOrElse(throw InternalError(s"CaseCustomField $ccf is boolean type but don't have boolean value"))
  override val name: String            = "boolean"
  override val writes: Writes[Boolean] = Writes.BooleanWrites
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override val setTypedValue = { case i: Int ⇒ CaseCustomField(integerValue = Some(i)) }
  override def getValue(ccf: CaseCustomField): Int =
    ccf.integerValue.getOrElse(throw InternalError(s"CaseCustomField $ccf is integer type but don't have integer value"))
  override val name: String        = "integer"
  override val writes: Writes[Int] = Writes.IntWrites
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override val setTypedValue = { case f: Float ⇒ CaseCustomField(floatValue = Some(f)) }
  override def getValue(ccf: CaseCustomField): Float =
    ccf.floatValue.getOrElse(throw InternalError(s"CaseCustomField $ccf is float type but don't have float value"))
  override val name: String          = "float"
  override val writes: Writes[Float] = Writes.FloatWrites
}

object CustomFieldDate extends CustomFieldType[Date] {
  override val setTypedValue = {
    case d: Date ⇒ CaseCustomField(dateValue = Some(d))
    case l: Long ⇒ CaseCustomField(dateValue = Some(new Date(l)))
  }
  override def getValue(ccf: CaseCustomField): Date =
    ccf.dateValue.getOrElse(throw InternalError(s"CaseCustomField $ccf is date type but don't have date value"))
  override val name: String         = "date"
  override val writes: Writes[Date] = Writes[Date](d ⇒ JsNumber(d.getTime))
}

@VertexEntity
case class CustomField(name: String, description: String, `type`: CustomFieldType[_])
