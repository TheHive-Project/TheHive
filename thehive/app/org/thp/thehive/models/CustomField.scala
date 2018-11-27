package org.thp.thehive.models

import java.util.Date

import play.api.libs.json.{JsNumber, Writes}

import org.thp.scalligraph._

abstract class CustomFieldAccessor[T, C] {
  def setValue(value: T): C
  def getValue(c: C): T
}

trait CustomFieldValue[C] { _: C ⇒
  val stringValue: Option[String]
  val booleanValue: Option[Boolean]
  val integerValue: Option[Int]
  val floatValue: Option[Float]
  val dateValue: Option[Date]
  def setStringValue(value: String): C
  def setBooleanValue(value: Boolean): C
  def setIntegerValue(value: Int): C
  def setFloatValue(value: Float): C
  def setDateValue(value: Date): C
}

sealed abstract class CustomFieldType[T] {
  def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C

  protected def setValueFailure(value: Any): Nothing =
    throw BadRequestError(s"""Invalid value type for custom field.
                             |  Expected: $name
                             |  Found   : $value (${value.getClass})
                           """.stripMargin)
  def getValue[C <: CustomFieldValue[C]](ccf: C): T

  protected def getValueFailure(ccf: CustomFieldValue[_]): Nothing =
    throw InternalError(s"CaseCustomField $ccf is $name typed but don't have $name value")

  val name: String
  val writes: Writes[T]
  override def toString: String = name
}

object CustomFieldString extends CustomFieldType[String] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C = value match {
    case v: String ⇒ customFieldValue.setStringValue(v)
    case _         ⇒ setValueFailure(value)
  }
  override def getValue[C <: CustomFieldValue[C]](ccf: C): String = ccf.stringValue.getOrElse(getValueFailure(ccf))
  override val name: String                                       = "string"
  override val writes: Writes[String]                             = Writes.StringWrites
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C = value match {
    case v: Boolean ⇒ customFieldValue.setBooleanValue(v)
    case _          ⇒ setValueFailure(value)
  }
  override def getValue[C <: CustomFieldValue[C]](ccf: C): Boolean = ccf.booleanValue.getOrElse(getValueFailure(ccf))
  override val name: String                                        = "boolean"
  override val writes: Writes[Boolean]                             = Writes.BooleanWrites
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C = value match {
    case v: Int ⇒ customFieldValue.setIntegerValue(v)
    case _      ⇒ setValueFailure(value)
  }
  override def getValue[C <: CustomFieldValue[C]](ccf: C): Int = ccf.integerValue.getOrElse(getValueFailure(ccf))
  override val name: String                                    = "integer"
  override val writes: Writes[Int]                             = Writes.IntWrites
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C = value match {
    case v: Float ⇒ customFieldValue.setFloatValue(v)
    case _        ⇒ setValueFailure(value)
  }
  override def getValue[C <: CustomFieldValue[C]](ccf: C): Float = ccf.floatValue.getOrElse(getValueFailure(ccf))
  override val name: String                                      = "float"
  override val writes: Writes[Float]                             = Writes.FloatWrites
}

object CustomFieldDate extends CustomFieldType[Date] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): C = value match {
    case v: Date ⇒ customFieldValue.setDateValue(v)
    case _       ⇒ setValueFailure(value)
  }
  override def getValue[C <: CustomFieldValue[C]](ccf: C): Date = ccf.dateValue.getOrElse(getValueFailure(ccf))
  override val name: String                                     = "date"
  override val writes: Writes[Date]                             = Writes[Date](d ⇒ JsNumber(d.getTime))
}

@VertexEntity
case class CustomField(name: String, description: String, `type`: CustomFieldType[_])
