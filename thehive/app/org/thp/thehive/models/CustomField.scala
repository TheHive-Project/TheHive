package org.thp.thehive.models

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json._

import gremlin.scala.Edge
import org.thp.scalligraph._
import org.thp.scalligraph.models.{Database, Entity, UniMapping}

trait CustomFieldValue[C] extends Product {
  val stringValue: Option[String]
  val booleanValue: Option[Boolean]
  val integerValue: Option[Int]
  val floatValue: Option[Float]
  val dateValue: Option[Date]
  def setStringValue(value: Option[String]): C
  def setBooleanValue(value: Option[Boolean]): C
  def setIntegerValue(value: Option[Int]): C
  def setFloatValue(value: Option[Float]): C
  def setDateValue(value: Option[Date]): C
}

class CustomFieldValueEdge(db: Database, edge: Edge) extends CustomFieldValue[CustomFieldValueEdge] {
  override val stringValue: Option[String]   = db.getOptionProperty(edge, "stringValue", UniMapping.string.optional)
  override val booleanValue: Option[Boolean] = db.getOptionProperty(edge, "booleanValue", UniMapping.boolean.optional)
  override val integerValue: Option[Int]     = db.getOptionProperty(edge, "intValue", UniMapping.int.optional)
  override val floatValue: Option[Float]     = db.getOptionProperty(edge, "floatValue", UniMapping.float.optional)
  override val dateValue: Option[Date]       = db.getOptionProperty(edge, "dateValue", UniMapping.date.optional)

  override def setStringValue(value: Option[String]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "stringValue", value, UniMapping.string.optional)
    new CustomFieldValueEdge(db, edge)
  }
  override def setBooleanValue(value: Option[Boolean]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "booleanValue", value, UniMapping.boolean.optional)
    new CustomFieldValueEdge(db, edge)
  }
  override def setIntegerValue(value: Option[Int]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "intValue", value, UniMapping.int.optional)
    new CustomFieldValueEdge(db, edge)
  }
  override def setFloatValue(value: Option[Float]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "floatValue", value, UniMapping.float.optional)
    new CustomFieldValueEdge(db, edge)
  }
  override def setDateValue(value: Option[Date]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "dateValue", value, UniMapping.date.optional)
    new CustomFieldValueEdge(db, edge)
  }
  override def productElement(n: Int): Any  = ???
  override def productArity: Int            = 0
  override def canEqual(that: Any): Boolean = that.isInstanceOf[CustomFieldValueEdge]
}

object CustomFieldType extends Enumeration {
  val string, integer, float, boolean, date = Value

  val map: Map[Value, CustomFieldType[_]] = Map(
    string  -> CustomFieldString,
    integer -> CustomFieldInteger,
    float   -> CustomFieldFloat,
    boolean -> CustomFieldBoolean,
    date    -> CustomFieldDate
  )
  def get(name: String): CustomFieldType[_] = map(this.withName(name))
}

sealed abstract class CustomFieldType[T] {
  val name: String
  val writes: Writes[T]

  def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C]

  def getValue(ccf: CustomFieldValue[_]): Option[T]

  def getJsonValue(ccf: CustomFieldValue[_]): JsValue = getValue(ccf).fold[JsValue](JsNull)(writes.writes)

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

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case v: String     => Success(customFieldValue.setStringValue(Some(v)))
    case JsString(v)   => Success(customFieldValue.setStringValue(Some(v)))
    case JsNull | null => Success(customFieldValue.setStringValue(None))
    case _             => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[String] = ccf.stringValue
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override val name: String            = "boolean"
  override val writes: Writes[Boolean] = Writes.BooleanWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case v: Boolean    => Success(customFieldValue.setBooleanValue(Some(v)))
    case JsBoolean(v)  => Success(customFieldValue.setBooleanValue(Some(v)))
    case JsNull | null => Success(customFieldValue.setBooleanValue(None))
    case _             => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Boolean] = ccf.booleanValue
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override val name: String        = "integer"
  override val writes: Writes[Int] = Writes.IntWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case v: Int        => Success(customFieldValue.setIntegerValue(Some(v)))
    case JsNumber(n)   => Success(customFieldValue.setIntegerValue(Some(n.toInt)))
    case JsNull | null => Success(customFieldValue.setIntegerValue(None))
    case _             => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override val name: String          = "float"
  override val writes: Writes[Float] = Writes.FloatWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case n: Number     => Success(customFieldValue.setFloatValue(Some(n.floatValue())))
    case JsNumber(n)   => Success(customFieldValue.setFloatValue(Some(n.toFloat)))
    case JsNull | null => Success(customFieldValue.setFloatValue(None))
    case _             => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Float] = ccf.floatValue
}

object CustomFieldDate extends CustomFieldType[Date] {
  override val name: String         = "date"
  override val writes: Writes[Date] = Writes[Date](d => JsNumber(d.getTime))

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case n: Number     => Success(customFieldValue.setDateValue(Some(new Date(n.longValue()))))
    case JsNumber(n)   => Success(customFieldValue.setDateValue(Some(new Date(n.toLong))))
    case v: Date       => Success(customFieldValue.setDateValue(Some(v)))
    case JsNull | null => Success(customFieldValue.setDateValue(None))
    case _             => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Date] = ccf.dateValue
}

@VertexEntity
case class CustomField(
    name: String,
    displayName: String,
    description: String,
    `type`: CustomFieldType.Value,
    mandatory: Boolean,
    options: Seq[JsValue]
)

case class RichCustomField(customField: CustomField with Entity, customFieldValue: CustomFieldValue[_] with Entity) {
  def name: String        = customField.name
  def description: String = customField.description

  def typeName: String = customField.`type`.toString

  def value: Option[Any] = `type`.getValue(customFieldValue)

  def `type`: CustomFieldType[_] = CustomFieldType.map(customField.`type`)

  def toJson: JsValue = value.fold[JsValue](JsNull)(`type`.writes.asInstanceOf[Writes[Any]].writes)
}
