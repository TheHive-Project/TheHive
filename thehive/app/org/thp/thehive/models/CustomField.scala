package org.thp.thehive.models

import java.util.Date

import scala.util.{Failure, Success, Try}

import play.api.libs.json._

import gremlin.scala.Edge
import org.thp.scalligraph._
import org.thp.scalligraph.models.{Database, Entity, Model, UniMapping}

trait CustomFieldValue[C] extends Product {
  def order: Option[Int]
  def stringValue: Option[String]
  def booleanValue: Option[Boolean]
  def integerValue: Option[Int]
  def floatValue: Option[Float]
  def dateValue: Option[Date]
  def order_=(value: Option[Int]): C
  def stringValue_=(value: Option[String]): C
  def booleanValue_=(value: Option[Boolean]): C
  def integerValue_=(value: Option[Int]): C
  def floatValue_=(value: Option[Float]): C
  def dateValue_=(value: Option[Date]): C
}

class CustomFieldValueEdge(db: Database, edge: Edge) extends CustomFieldValue[CustomFieldValueEdge] with Entity {
  override def order: Option[Int]            = db.getOptionProperty(edge, "order", UniMapping.int.optional)
  override def stringValue: Option[String]   = db.getOptionProperty(edge, "stringValue", UniMapping.string.optional)
  override def booleanValue: Option[Boolean] = db.getOptionProperty(edge, "booleanValue", UniMapping.boolean.optional)
  override def integerValue: Option[Int]     = db.getOptionProperty(edge, "intValue", UniMapping.int.optional)
  override def floatValue: Option[Float]     = db.getOptionProperty(edge, "floatValue", UniMapping.float.optional)
  override def dateValue: Option[Date]       = db.getOptionProperty(edge, "dateValue", UniMapping.date.optional)

  override def order_=(value: Option[Int]): CustomFieldValueEdge = {
    db.setProperty(edge, "order", value, UniMapping.int.optional)
    this
  }

  override def stringValue_=(value: Option[String]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "stringValue", value, UniMapping.string.optional)
    this
  }
  override def booleanValue_=(value: Option[Boolean]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "booleanValue", value, UniMapping.boolean.optional)
    this
  }
  override def integerValue_=(value: Option[Int]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "intValue", value, UniMapping.int.optional)
    this
  }
  override def floatValue_=(value: Option[Float]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "floatValue", value, UniMapping.float.optional)
    this
  }
  override def dateValue_=(value: Option[Date]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "dateValue", value, UniMapping.date.optional)
    this
  }
  override def productElement(n: Int): Any  = ???
  override def productArity: Int            = 0
  override def canEqual(that: Any): Boolean = that.isInstanceOf[CustomFieldValueEdge]

  override val _id: String                = edge.id().toString
  override val _model: Model              = null
  override val _createdBy: String         = db.getSingleProperty(edge, "_createdBy", UniMapping.string)
  override val _updatedBy: Option[String] = db.getOptionProperty(edge, "_updatedBy", UniMapping.string.optional)
  override val _createdAt: Date           = db.getSingleProperty(edge, "_createdAt", UniMapping.date)
  override val _updatedAt: Option[Date]   = db.getOptionProperty(edge, "_updatedAt", UniMapping.date.optional)
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
    case v: String     => Success(customFieldValue.stringValue = Some(v))
    case JsString(v)   => Success(customFieldValue.stringValue = Some(v))
    case JsNull | null => Success(customFieldValue.stringValue = None)
    case obj: JsObject =>
      customFieldValue.stringValue = (obj \ "string").asOpt[String]
      customFieldValue.order = (obj \ "order").asOpt[Int]
      Success(customFieldValue)
    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[String] = ccf.stringValue
}

object CustomFieldBoolean extends CustomFieldType[Boolean] {
  override val name: String            = "boolean"
  override val writes: Writes[Boolean] = Writes.BooleanWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case v: Boolean    => Success(customFieldValue.booleanValue = Some(v))
    case JsBoolean(v)  => Success(customFieldValue.booleanValue = Some(v))
    case JsNull | null => Success(customFieldValue.booleanValue = None)
    case obj: JsObject =>
      customFieldValue.booleanValue = (obj \ "boolean").asOpt[Boolean]
      customFieldValue.order = (obj \ "order").asOpt[Int]
      Success(customFieldValue)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Boolean] = ccf.booleanValue
}

object CustomFieldInteger extends CustomFieldType[Int] {
  override val name: String        = "integer"
  override val writes: Writes[Int] = Writes.IntWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case v: Int        => Success(customFieldValue.integerValue = Some(v))
    case JsNumber(n)   => Success(customFieldValue.integerValue = Some(n.toInt))
    case JsNull | null => Success(customFieldValue.integerValue = None)
    case obj: JsObject =>
      customFieldValue.integerValue = (obj \ "integer").asOpt[Int]
      customFieldValue.order = (obj \ "order").asOpt[Int]
      Success(customFieldValue)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue
}

object CustomFieldFloat extends CustomFieldType[Float] {
  override val name: String          = "float"
  override val writes: Writes[Float] = Writes.FloatWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case n: Number     => Success(customFieldValue.floatValue = Some(n.floatValue()))
    case JsNumber(n)   => Success(customFieldValue.floatValue = Some(n.toFloat))
    case JsNull | null => Success(customFieldValue.floatValue = None)
    case obj: JsObject =>
      customFieldValue.floatValue = (obj \ "float").asOpt[Float]
      customFieldValue.order = (obj \ "order").asOpt[Int]
      Success(customFieldValue)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Float] = ccf.floatValue
}

object CustomFieldDate extends CustomFieldType[Date] {
  override val name: String         = "date"
  override val writes: Writes[Date] = Writes[Date](d => JsNumber(d.getTime))

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case n: Number     => Success(customFieldValue.dateValue = Some(new Date(n.longValue())))
    case JsNumber(n)   => Success(customFieldValue.dateValue = Some(new Date(n.toLong)))
    case v: Date       => Success(customFieldValue.dateValue = Some(v))
    case JsNull | null => Success(customFieldValue.dateValue = None)
    case obj: JsObject =>
      customFieldValue.dateValue = (obj \ "date").asOpt[Long].map(new Date(_))
      customFieldValue.order = (obj \ "order").asOpt[Int]
      Success(customFieldValue)

    case _ => setValueFailure(value)
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
