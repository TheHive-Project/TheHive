package org.thp.thehive.models

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph._
import org.thp.scalligraph.controllers.{Output, Renderer}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PredicateOps
import org.thp.scalligraph.traversal.Traversal.Domain
import org.thp.scalligraph.traversal.{Traversal, TraversalOps}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.util.Date
import scala.util.{Failure, Success, Try}

@BuildEdgeEntity[CustomFieldValue, CustomField]
case class CustomFieldValueCustomField()

@BuildVertexEntity
@DefineIndex(IndexType.standard, "elementId")
@DefineIndex(IndexType.standard, "name")
@DefineIndex(IndexType.standard, "stringValue")
@DefineIndex(IndexType.standard, "booleanValue")
@DefineIndex(IndexType.standard, "integerValue")
@DefineIndex(IndexType.standard, "floatValue")
@DefineIndex(IndexType.standard, "dateValue")
case class CustomFieldValue(
    elementId: EntityId,
    name: String,
    order: Option[Int] = None,
    stringValue: Option[String] = None,
    booleanValue: Option[Boolean] = None,
    integerValue: Option[Int] = None,
    floatValue: Option[Double] = None,
    dateValue: Option[Date] = None
)

object CustomFieldType {
  def withName(name: String): CustomFieldType =
    name match {
      case "string"  => CustomFieldString
      case "integer" => CustomFieldInteger
      case "float"   => CustomFieldFloat
      case "boolean" => CustomFieldBoolean
      case "date"    => CustomFieldDate
      case other     => throw InternalError(s"Invalid CustomFieldType (found: $other, expected: string, integer, float, boolean or date)")
    }
  implicit val renderer: Renderer[CustomFieldType] = new Renderer[CustomFieldType] {
    override type O = String
    override def toOutput(value: CustomFieldType): Output[String] = Output(value.name)
  }
  implicit val mapping: SingleMapping[CustomFieldType, String] =
    SingleMapping[CustomFieldType, String](toGraph = t => t.name, toDomain = withName)
}

sealed abstract class CustomFieldType extends TraversalOps with PredicateOps {
  type T
  val name: String
  val format: Format[T]

  protected def fail(value: JsValue): Failure[Nothing] =
    Failure(BadRequestError(s"""Invalid value type for custom field.
                               |  Expected: $name
                               |  Found   : $value (${value.getClass})
                             """.stripMargin))

  def readValue(value: JsValue): Try[Option[T]] =
    value match {
      case JsNull => Success(None)
      case other  => other.asOpt(format).fold[Try[Option[T]]](fail(value))(v => Success(Some(v)))
    }

  lazy val parseReader: Reads[(JsValue, Option[Int])] =
    Reads[(JsValue, Option[Int])](j => j.validate(format).map(_ => j -> None)).orElse {
      ((__ \ "value").read(format).map(Option(_)).orElse((__ \ name).readNullable(format)) and
        (__ \ "order").readNullable[Int]).apply((v, o) => (v.fold[JsValue](JsNull)(format.writes), o))
    }

  def parseValue(value: JsValue): Try[(JsValue, Option[Int])] =
    parseReader
      .reads(value)
      .fold(
        _ => fail(value),
        Success(_)
      )

  def getValue(ccf: CustomFieldValue): Option[T]
  def getJsonValue(ccf: CustomFieldValue): JsValue = getValue(ccf).fold[JsValue](JsNull)(format.writes)
  def getValue(traversal: Traversal.V[CustomFieldValue]): Traversal.Domain[T]
  def getJsonValue(traversal: Traversal.V[CustomFieldValue]): Traversal.Domain[JsValue] = getValue(traversal).domainMap(format.writes)
  def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue]
  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue]
  def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]]
  override def toString: String = name
}

object CustomFieldString extends CustomFieldType with TraversalOps {
  override type T = String
  override val name: String           = "string"
  override val format: Format[String] = implicitly[Format[String]]

  override def getValue(ccf: CustomFieldValue): Option[String] = ccf.stringValue

  override def getValue(traversal: Traversal.V[CustomFieldValue]): Traversal.Domain[String] = traversal.value(_.stringValue).castDomain

  override def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue] =
    Try(traversal.has(_.stringValue, predicate.mapValue(_.as(format)))).getOrElse(traversal.empty)

  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue] =
    value match {
      case JsString(v) => Success(customFieldValue.copy(stringValue = Some(v)))
      case JsNull      => Success(customFieldValue.copy(stringValue = None))
      case _           => fail(value)
    }

  def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]] =
    readValue(value).map(v => traversal.update(_.stringValue, v))
}

object CustomFieldBoolean extends CustomFieldType with TraversalOps {
  override type T = Boolean
  override val name: String            = "boolean"
  override val format: Format[Boolean] = implicitly[Format[Boolean]]

  override def getValue(ccf: CustomFieldValue): Option[Boolean] = ccf.booleanValue

  override def getValue(traversal: Traversal.V[CustomFieldValue]): Traversal.Domain[Boolean] = traversal.value(_.booleanValue).castDomain

  override def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue] =
    Try(traversal.has(_.booleanValue, predicate.mapValue(_.as(format)))).getOrElse(traversal.empty)

  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue] =
    value match {
      case JsBoolean(v) => Success(customFieldValue.copy(booleanValue = Some(v)))
      case JsNull       => Success(customFieldValue.copy(booleanValue = None))
      case _            => fail(value)
    }

  def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]] =
    readValue(value).map(v => traversal.update(_.booleanValue, v))
}

object CustomFieldInteger extends CustomFieldType with TraversalOps {
  override type T = Int
  override val name: String        = "integer"
  override val format: Format[Int] = implicitly[Format[Int]]

  override def getValue(ccf: CustomFieldValue): Option[Int] = ccf.integerValue

  override def getValue(traversal: Traversal.V[CustomFieldValue]): Domain[Int] = traversal.value(_.integerValue).castDomain

  override def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue] =
    Try(traversal.has(_.integerValue, predicate.mapValue(_.as(format)))).getOrElse(traversal.empty)

  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue] =
    value match {
      case JsNumber(v) => Success(customFieldValue.copy(integerValue = Some(v.toInt)))
      case JsNull      => Success(customFieldValue.copy(integerValue = None))
      case _           => fail(value)
    }

  override def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]] =
    readValue(value).map(v => traversal.update(_.integerValue, v))
}

object CustomFieldFloat extends CustomFieldType with TraversalOps {
  override type T = Double
  override val name: String                                    = "float"
  override val format: Format[Double]                          = implicitly[Format[Double]]
  override def getValue(ccf: CustomFieldValue): Option[Double] = ccf.floatValue

  override def getValue(traversal: Traversal.V[CustomFieldValue]): Domain[Double] = traversal.value(_.floatValue).castDomain

  override def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue] =
    Try(traversal.has(_.floatValue, predicate.mapValue(_.as(format)))).getOrElse(traversal.empty)

  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue] =
    value match {
      case JsNumber(v) => Success(customFieldValue.copy(floatValue = Some(v.toDouble)))
      case JsNull      => Success(customFieldValue.copy(floatValue = None))
      case _           => fail(value)
    }

  override def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]] =
    readValue(value).map(v => traversal.update(_.floatValue, v))
}

object CustomFieldDate extends CustomFieldType with TraversalOps {
  override type T = Date
  override val name: String         = "date"
  override val format: Format[Date] = Format[Date](Reads.LongReads.map(new Date(_)), Writes.LongWrites.contramap(_.getTime))

  override def getValue(ccf: CustomFieldValue): Option[Date] = ccf.dateValue

  override def getValue(traversal: Traversal.V[CustomFieldValue]): Domain[Date] = traversal.value(_.dateValue).castDomain

  override def filter(traversal: Traversal.V[CustomFieldValue], predicate: P[JsValue]): Traversal.V[CustomFieldValue] =
    Try(traversal.has(_.dateValue, predicate.mapValue(_.as(format)))).getOrElse(traversal.empty)

  def setValue(customFieldValue: CustomFieldValue, value: JsValue): Try[CustomFieldValue] =
    value match {
      case JsNumber(v) => Success(customFieldValue.copy(dateValue = Some(new Date(v.toLong))))
      case JsNull      => Success(customFieldValue.copy(dateValue = None))
      case _           => fail(value)
    }

  override def updateValue(traversal: Traversal.V[CustomFieldValue], value: JsValue): Try[Traversal.V[CustomFieldValue]] =
    readValue(value).map(v => traversal.update(_.dateValue, v))
}

@DefineIndex(IndexType.unique, "name")
@BuildVertexEntity
case class CustomField(
    name: String,
    displayName: String,
    description: String,
    `type`: CustomFieldType,
    mandatory: Boolean,
    options: Seq[JsValue]
)

case class RichCustomField(customField: CustomField with Entity, customFieldValue: CustomFieldValue with Entity) {
  def name: String            = customField.name
  def description: String     = customField.description
  def typeName: String        = customField.`type`.toString
  def value: Option[Any]      = `type`.getValue(customFieldValue)
  def jsValue: JsValue        = `type`.getJsonValue(customFieldValue)
  def order: Option[Int]      = customFieldValue.order
  def `type`: CustomFieldType = customField.`type`
  def toJson: JsValue         = value.fold[JsValue](JsNull)(`type`.format.asInstanceOf[Format[Any]].writes)
}
