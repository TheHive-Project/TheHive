package org.thp.thehive.models

import java.util.Date

import gremlin.scala.Edge
import javax.inject.Named
import org.thp.scalligraph._
import org.thp.scalligraph.models._
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

trait CustomFieldValue[C] extends Product {
  def order: Option[Int]
  def stringValue: Option[String]
  def booleanValue: Option[Boolean]
  def integerValue: Option[Int]
  def floatValue: Option[Double]
  def dateValue: Option[Date]
  def order_=(value: Option[Int]): C
  def stringValue_=(value: Option[String]): C
  def booleanValue_=(value: Option[Boolean]): C
  def integerValue_=(value: Option[Int]): C
  def floatValue_=(value: Option[Double]): C
  def dateValue_=(value: Option[Date]): C
}

class CustomFieldValueEdge(@Named("with-thehive-schema") db: Database, edge: Edge) extends CustomFieldValue[CustomFieldValueEdge] with Entity {
  override def order: Option[Int]            = db.getOptionProperty(edge, "order", UniMapping.int.optional)
  override def stringValue: Option[String]   = db.getOptionProperty(edge, "stringValue", UniMapping.string.optional)
  override def booleanValue: Option[Boolean] = db.getOptionProperty(edge, "booleanValue", UniMapping.boolean.optional)
  override def integerValue: Option[Int]     = db.getOptionProperty(edge, "integerValue", UniMapping.int.optional)
  override def floatValue: Option[Double]    = db.getOptionProperty(edge, "floatValue", UniMapping.double.optional)
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
    db.setOptionProperty(edge, "integerValue", value, UniMapping.int.optional)
    this
  }
  override def floatValue_=(value: Option[Double]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "floatValue", value, UniMapping.double.optional)
    this
  }
  override def dateValue_=(value: Option[Date]): CustomFieldValueEdge = {
    db.setOptionProperty(edge, "dateValue", value, UniMapping.date.optional)
    this
  }
  override def productElement(n: Int): Any  = ???
  override def productArity: Int            = 0
  override def canEqual(that: Any): Boolean = that.isInstanceOf[CustomFieldValueEdge]

  override def _id: String                = edge.id().toString
  override def _model: Model              = ???
  override def _createdBy: String         = db.getSingleProperty(edge, "_createdBy", UniMapping.string)
  override def _updatedBy: Option[String] = db.getOptionProperty(edge, "_updatedBy", UniMapping.string.optional)
  override def _createdAt: Date           = db.getSingleProperty(edge, "_createdAt", UniMapping.date)
  override def _updatedAt: Option[Date]   = db.getOptionProperty(edge, "_updatedAt", UniMapping.date.optional)
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
      val stringValue = (obj \ "string").asOpt[String]
      val order       = (obj \ "order").asOpt[Int]
      Success((customFieldValue.stringValue = stringValue).order = order)
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
      val booleanValue = (obj \ "boolean").asOpt[Boolean]
      val order        = (obj \ "order").asOpt[Int]
      Success((customFieldValue.booleanValue = booleanValue).order = order)

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
      val integerValue = (obj \ "integer").asOpt[Int]
      val order        = (obj \ "order").asOpt[Int]
      Success((customFieldValue.integerValue = integerValue).order = order)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue
}

object CustomFieldFloat extends CustomFieldType[Double] {
  override val name: String           = "float"
  override val writes: Writes[Double] = Writes.DoubleWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] = value.getOrElse(JsNull) match {
    case n: Number     => Success(customFieldValue.floatValue = Some(n.doubleValue()))
    case JsNumber(n)   => Success(customFieldValue.floatValue = Some(n.toDouble))
    case JsNull | null => Success(customFieldValue.floatValue = None)
    case obj: JsObject =>
      val floatValue = (obj \ "float").asOpt[Double]
      val order      = (obj \ "order").asOpt[Int]
      Success((customFieldValue.floatValue = floatValue).order = order)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Double] = ccf.floatValue
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
      val dateValue = (obj \ "date").asOpt[Long].map(new Date(_))
      val order     = (obj \ "order").asOpt[Int]
      Success((customFieldValue.dateValue = dateValue).order = order)

    case _ => setValueFailure(value)
  }

  override def getValue(ccf: CustomFieldValue[_]): Option[Date] = ccf.dateValue
}

@DefineIndex(IndexType.unique, "name")
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
  def name: String               = customField.name
  def description: String        = customField.description
  def typeName: String           = customField.`type`.toString
  def value: Option[Any]         = `type`.getValue(customFieldValue)
  def order: Option[Int]         = customFieldValue.order
  def `type`: CustomFieldType[_] = CustomFieldType.map(customField.`type`)
  def toJson: JsValue            = value.fold[JsValue](JsNull)(`type`.writes.asInstanceOf[Writes[Any]].writes)
}
