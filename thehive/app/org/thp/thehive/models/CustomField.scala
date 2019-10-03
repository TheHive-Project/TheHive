package org.thp.thehive.models

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json._

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
  val name: String
  val writes: Writes[T]

  def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C]

  def getValue(ccf: CustomFieldValue[_]): Option[T]

  override def toString: String = name

  protected def setValueFailure(value: Any): Failure[Nothing] =
    Failure(BadRequestError(s"""Invalid value type for custom field.
                               |  Expected: $name
                               |  Found   : $value (${value.getClass})
                             """.stripMargin))
}

object CustomFieldString extends CustomFieldType[String] {
  override val name: String           = "string"
  override val writes: Writes[String] = Writes.StringWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: String   => Success(customFieldValue.setStringValue(v))
    case v: JsString => Success(customFieldValue.setStringValue(v.toString))
    case _           => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[String] = ccf.stringValue
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override val name: String            = "boolean"
  override val writes: Writes[Boolean] = Writes.BooleanWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: Boolean   => Success(customFieldValue.setBooleanValue(v))
    case v: JsBoolean => Success(customFieldValue.setBooleanValue(v.as[Boolean]))
    case _            => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Boolean] = ccf.booleanValue
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override val name: String        = "integer"
  override val writes: Writes[Int] = Writes.IntWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case v: Int => Success(customFieldValue.setIntegerValue(v))
    case v: JsNumber =>
      v.validate[Int]
        .map(customFieldValue.setIntegerValue)
        .fold(
          _ => setValueFailure(v),
          res => Success(res)
        )
    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override val name: String          = "float"
  override val writes: Writes[Float] = Writes.FloatWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case n: Number => Success(customFieldValue.setFloatValue(n.floatValue()))
    case n: JsNumber =>
      n.validate[Float]
        .map(customFieldValue.setFloatValue)
        .fold(
          _ => setValueFailure(n),
          res => Success(res)
        )
    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Float] = ccf.floatValue
}

object CustomFieldDate extends CustomFieldType[Date] {
  override val name: String         = "date"
  override val writes: Writes[Date] = Writes[Date](d => JsNumber(d.getTime))

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Any): Try[C] = value match {
    case n: Number => Success(customFieldValue.setDateValue(new Date(n.longValue())))
    case n: JsNumber =>
      n.validate[Long]
        .map(l => customFieldValue.setDateValue(new Date(l)))
        .fold(
          _ => setValueFailure(n),
          res => Success(res)
        )
    case v: Date => Success(customFieldValue.setDateValue(v))
    case _       => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Date] = ccf.dateValue
}

@VertexEntity
case class CustomField(
    name: String,
    displayName: String,
    description: String,
    `type`: CustomFieldType[_],
    mandatory: Boolean,
    options: Seq[JsValue]
)

object CustomField {

  def fromString(name: String): Try[CustomFieldType[_]] = name match {
    case "string"  => Success(CustomFieldString)
    case "boolean" => Success(CustomFieldBoolean)
    case "integer" => Success(CustomFieldInteger)
    case "float"   => Success(CustomFieldFloat)
    case "date"    => Success(CustomFieldDate)
    case _         => Failure(new Exception(s"Invalid name $name as CustomFieldType"))
  }
}

case class CustomFieldWithValue(customField: CustomField with Entity, customFieldValue: CustomFieldValue[_] with Entity) {
  def name: String        = customField.name
  def description: String = customField.description

  def typeName: String = customField.`type`.name

  def value: Option[Any] = `type`.getValue(customFieldValue)

  def `type`: CustomFieldType[_] = customField.`type`
}
