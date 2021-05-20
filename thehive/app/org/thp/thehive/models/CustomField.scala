package org.thp.thehive.models

import org.apache.tinkerpop.gremlin.structure.Edge
import org.thp.scalligraph._
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.Traversal.{Domain, E}
import org.thp.scalligraph.traversal.{Traversal, TraversalOps}
import play.api.libs.json._

import java.util.{Date, NoSuchElementException}
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

class CustomFieldValueEdge(edge: Edge) extends CustomFieldValue[CustomFieldValueEdge] with Entity {
  override def order: Option[Int]            = UMapping.int.optional.getProperty(edge, "order")
  override def stringValue: Option[String]   = UMapping.string.optional.getProperty(edge, "stringValue")
  override def booleanValue: Option[Boolean] = UMapping.boolean.optional.getProperty(edge, "booleanValue")
  override def integerValue: Option[Int]     = UMapping.int.optional.getProperty(edge, "integerValue")
  override def floatValue: Option[Double]    = UMapping.double.optional.getProperty(edge, "floatValue")
  override def dateValue: Option[Date]       = UMapping.date.optional.getProperty(edge, "dateValue")

  override def order_=(value: Option[Int]): CustomFieldValueEdge = {
    UMapping.int.optional.setProperty(edge, "order", value)
    this
  }

  override def stringValue_=(value: Option[String]): CustomFieldValueEdge = {
    UMapping.string.optional.setProperty(edge, "stringValue", value)
    this
  }
  override def booleanValue_=(value: Option[Boolean]): CustomFieldValueEdge = {
    UMapping.boolean.optional.setProperty(edge, "booleanValue", value)
    this
  }
  override def integerValue_=(value: Option[Int]): CustomFieldValueEdge = {
    UMapping.int.optional.setProperty(edge, "integerValue", value)
    this
  }
  override def floatValue_=(value: Option[Double]): CustomFieldValueEdge = {
    UMapping.double.optional.setProperty(edge, "floatValue", value)
    this
  }
  override def dateValue_=(value: Option[Date]): CustomFieldValueEdge = {
    UMapping.date.optional.setProperty(edge, "dateValue", value)
    this
  }
  override def productElement(n: Int): Any  = throw new NoSuchElementException
  override def productArity: Int            = 0
  override def canEqual(that: Any): Boolean = that.isInstanceOf[CustomFieldValueEdge]

  override def _id: EntityId              = EntityId(edge.id())
  override def _label: String             = edge.label()
  override def _createdBy: String         = UMapping.string.getProperty(edge, "_createdBy")
  override def _updatedBy: Option[String] = UMapping.string.optional.getProperty(edge, "_updatedBy")
  override def _createdAt: Date           = UMapping.date.getProperty(edge, "_createdAt")
  override def _updatedAt: Option[Date]   = UMapping.date.optional.getProperty(edge, "_updatedAt")
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

  def getValue[C <: CustomFieldValue[_]](traversal: Traversal.E[C]): Traversal.Domain[T]

  def getJsonValue[C <: CustomFieldValue[_]](traversal: Traversal.E[C]): Traversal.Domain[JsValue] = getValue(traversal).domainMap(writes.writes)

  override def toString: String = name

  protected def setValueFailure(value: Any): Failure[Nothing] =
    Failure(BadRequestError(s"""Invalid value type for custom field.
                               |  Expected: $name
                               |  Found   : $value (${value.getClass})
                             """.stripMargin))
}

object CustomFieldString extends CustomFieldType[String] with TraversalOps {
  override val name: String           = "string"
  override val writes: Writes[String] = Writes.StringWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] =
    value.getOrElse(JsNull) match {
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

  override def getValue[C <: CustomFieldValue[_]](traversal: E[C]): Traversal.Domain[String] = traversal.value(_.stringValue).castDomain
}

object CustomFieldBoolean extends CustomFieldType[Boolean] with TraversalOps {
  override val name: String            = "boolean"
  override val writes: Writes[Boolean] = Writes.BooleanWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] =
    value.getOrElse(JsNull) match {
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

  override def getValue[C <: CustomFieldValue[_]](traversal: E[C]): Domain[Boolean] = traversal.value(_.booleanValue).castDomain
}

object CustomFieldInteger extends CustomFieldType[Int] with TraversalOps {
  override val name: String        = "integer"
  override val writes: Writes[Int] = Writes.IntWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] =
    value.getOrElse(JsNull) match {
      case v: Int        => Success(customFieldValue.integerValue = Some(v))
      case v: Double     => Success(customFieldValue.integerValue = Some(v.toInt))
      case JsNumber(n)   => Success(customFieldValue.integerValue = Some(n.toInt))
      case JsNull | null => Success(customFieldValue.integerValue = None)
      case obj: JsObject =>
        val integerValue = (obj \ "integer").asOpt[Int]
        val order        = (obj \ "order").asOpt[Int]
        Success((customFieldValue.integerValue = integerValue).order = order)

      case _ => setValueFailure(value)
    }

  override def getValue(ccf: CustomFieldValue[_]): Option[Int] = ccf.integerValue

  override def getValue[C <: CustomFieldValue[_]](traversal: E[C]): Domain[Int] = traversal.value(_.integerValue).castDomain
}

object CustomFieldFloat extends CustomFieldType[Double] with TraversalOps {
  override val name: String           = "float"
  override val writes: Writes[Double] = Writes.DoubleWrites

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] =
    value.getOrElse(JsNull) match {
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

  override def getValue[C <: CustomFieldValue[_]](traversal: E[C]): Domain[Double] = traversal.value(_.floatValue).castDomain
}

object CustomFieldDate extends CustomFieldType[Date] with TraversalOps {
  override val name: String         = "date"
  override val writes: Writes[Date] = Writes[Date](d => JsNumber(d.getTime))

  override def setValue[C <: CustomFieldValue[C]](customFieldValue: C, value: Option[Any]): Try[C] =
    value.getOrElse(JsNull) match {
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

  override def getValue[C <: CustomFieldValue[_]](traversal: E[C]): Domain[Date] = traversal.value(_.dateValue).castDomain
}

@DefineIndex(IndexType.unique, "name")
@BuildVertexEntity
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
  def jsValue: JsValue           = `type`.getJsonValue(customFieldValue)
  def order: Option[Int]         = customFieldValue.order
  def `type`: CustomFieldType[_] = CustomFieldType.map(customField.`type`)
  def toJson: JsValue            = value.fold[JsValue](JsNull)(`type`.writes.asInstanceOf[Writes[Any]].writes)
}
