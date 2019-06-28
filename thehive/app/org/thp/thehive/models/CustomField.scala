package org.thp.thehive.models

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json.{JsNumber, Writes}

import org.thp.scalligraph._
import org.thp.scalligraph.models.Entity

abstract class CustomFieldAccessor[T, C] {
  def setValue(value: T): C
  def getValue(c: C): T
}

trait CustomFieldValue[C] { _: C =>
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
  def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C]

  protected def setValueFailure(value: Any): Failure[Nothing] =
    Failure(BadRequestError(s"""Invalid value type for custom field.
                               |  Expected: $name
                               |  Found   : $value (${value.getClass})
                             """.stripMargin))
  def getValue(ccf: CustomFieldValue[_]): Option[T]

  val name: String
  val writes: Writes[T]
  override def toString: String = name
}

object CustomFieldString extends CustomFieldType[String] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: String => Success(customFieldValue.setStringValue(v))
    case _         => setValueFailure(value)
  }
  override def getValue(ccf: CustomFieldValue[_]): Option[String] = ccf.stringValue
  override val name: String                                       = "string"
  override val writes: Writes[String]                             = Writes.StringWrites
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: Boolean => Success(customFieldValue.setBooleanValue(v))
    case _          => setValueFailure(value)
  }
  override def getValue(ccf: CustomFieldValue[_]): Option[Boolean] = ccf.booleanValue
  override val name: String                                        = "boolean"
  override val writes: Writes[Boolean]                             = Writes.BooleanWrites
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: Int => Success(customFieldValue.setIntegerValue(v))
    case _      => setValueFailure(value)
  }
  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue
  override val name: String                                    = "integer"
  override val writes: Writes[Int]                             = Writes.IntWrites
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case n: Number => Success(customFieldValue.setFloatValue(n.floatValue()))
    case _         => setValueFailure(value)
  }
  override def getValue(ccf: CustomFieldValue[_]): Option[Float] = ccf.floatValue
  override val name: String                                      = "float"
  override val writes: Writes[Float]                             = Writes.FloatWrites
}

object CustomFieldDate extends CustomFieldType[Date] {
  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case n: Number => Success(customFieldValue.setDateValue(new Date(n.longValue())))
    case v: Date   => Success(customFieldValue.setDateValue(v))
    case _         => setValueFailure(value)
  }
  override def getValue(ccf: CustomFieldValue[_]): Option[Date] = ccf.dateValue
  override val name: String                                     = "date"
  override val writes: Writes[Date]                             = Writes[Date](d => JsNumber(d.getTime))
}

@VertexEntity
case class CustomField(name: String, description: String, `type`: CustomFieldType[_])

case class CustomFieldWithValue(customField: CustomField with Entity, customFieldValue: CustomFieldValue[_] with Entity) {
  def name: String               = customField.name
  def description: String        = customField.description
  def `type`: CustomFieldType[_] = customField.`type`
  def typeName: String           = customField.`type`.name
  def value: Option[Any]         = `type`.getValue(customFieldValue)
}
