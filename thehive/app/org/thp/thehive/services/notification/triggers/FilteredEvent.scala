package org.thp.thehive.services.notification.triggers

import com.typesafe.config.ConfigRenderOptions
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.models.{Audit, Organisation, User}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import javax.inject.{Inject, Singleton}
import scala.util.{Success, Try}

object EventFilterOnMissingUser extends Exception

trait EventFilter {
  def getField[T](event: JsValue, name: String)(implicit reads: Reads[T]): Option[T] =
    name.split('.').foldLeft[JsLookupResult](JsDefined(event))(_ \ _).asOpt[T].orElse {
      if (name == "user") throw EventFilterOnMissingUser
      else None
    }
  def apply(event: JsObject): Boolean
}

object AnyEventFilter extends EventFilter {
  override def apply(event: JsObject): Boolean = true
}
case class AndEventFilter(filters: Seq[EventFilter]) extends EventFilter {
  override def apply(event: JsObject): Boolean = filters.forall(_.apply(event))
}
case class OrEventFilter(filters: Seq[EventFilter]) extends EventFilter {
  override def apply(event: JsObject): Boolean = filters.exists(_.apply(event))
}
case class NotEventFilter(filter: EventFilter) extends EventFilter {
  override def apply(event: JsObject): Boolean = !filter.apply(event)
}
case class LtEventFilter(field: String, value: BigDecimal) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[BigDecimal](event, field).fold(false)(_ < value)
}
case class GtEventFilter(field: String, value: BigDecimal) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[BigDecimal](event, field).fold(false)(_ > value)
}
case class LteEventFilter(field: String, value: BigDecimal) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[BigDecimal](event, field).fold(false)(_ <= value)
}
case class GteEventFilter(field: String, value: BigDecimal) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[BigDecimal](event, field).fold(false)(_ >= value)
}
case class IsEventFilter(field: String, value: JsValue) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[JsValue](event, field).fold(false)(_ == value)
}
case class StartsWithEventFilter(field: String, value: String) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[String](event, field).fold(false)(_.startsWith(value))
}
case class EndsWithEventFilter(field: String, value: String) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[String](event, field).fold(false)(_.endsWith(value))
}
case class BetweenEventFilter(field: String, from: BigDecimal, to: BigDecimal) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[BigDecimal](event, field).fold(false)(v => from <= v && v < to)
}
case class InEventFilter(field: String, values: Seq[JsValue]) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[JsValue](event, field).fold(false)(values.contains)
}
case class ContainsEventFilter(field: String, value: String) extends EventFilter {
  override def apply(event: JsObject): Boolean = getField[String](event, field).fold(false)(_.contains(value))
}
case class LikeEventFilter(field: String, value: String) extends EventFilter {
  lazy val s: Boolean = value.headOption.contains('*')
  lazy val e: Boolean = value.lastOption.contains('*')
  override def apply(event: JsObject): Boolean =
    getField[String](event, field).fold(false) {
      case v if s && e => v.contains(value.tail.dropRight(1))
      case v if s      => v.endsWith(value)
      case v if e      => v.startsWith(value)
      case v           => v == value
    }

}

object EventFilter {
  implicit val fieldValueReads: Reads[(String, JsValue)] = Reads[(String, JsValue)] {
    case obj: JsObject if obj.fields.size == 1 => JsSuccess(obj.fields.head)
    case _                                     => JsError("An object with only one field is expected")
  }

  implicit val fieldStringValueReads: Reads[(String, String)] = Reads[(String, String)] {
    case obj: JsObject if obj.fields.size == 1 =>
      obj.fields.head match {
        case (k, JsString(s)) => JsSuccess(k -> s)
        case _                => JsError("An object with only one string field is expected")
      }
    case _ => JsError("An object with only one string field is expected")
  }
  implicit val fieldNumberValueReads: Reads[(String, BigDecimal)] = Reads[(String, BigDecimal)] {
    case obj: JsObject if obj.fields.size == 1 =>
      obj.fields.head match {
        case (k, JsNumber(n)) => JsSuccess(k -> n)
        case _                => JsError("An object with only one number field is expected")
      }
    case _ => JsError("An object with only one number field is expected")
  }

  implicit val fieldValuesReads: Reads[InEventFilter] =
    ((JsPath \ "_field").read[String] and
      (JsPath \ "_values").read[Seq[JsValue]])(InEventFilter.apply _)

  implicit val betweenEventFilterReads: Reads[BetweenEventFilter] =
    ((JsPath \ "_field").read[String] and
      (JsPath \ "_from").read[BigDecimal] and
      (JsPath \ "_to").read[BigDecimal])(BetweenEventFilter.apply _)

  implicit lazy val reads: Reads[EventFilter] =
    (JsPath \ "_any").read[JsValue].map(_ => AnyEventFilter.asInstanceOf[EventFilter]) orElse
      (JsPath \ "_and").lazyRead[Seq[EventFilter]](Reads.seq(reads)).map(AndEventFilter) orElse
      (JsPath \ "_or").lazyRead[Seq[EventFilter]](Reads.seq(reads)).map(OrEventFilter) orElse
      (JsPath \ "_not").lazyRead[EventFilter](reads).map(NotEventFilter) orElse
      (JsPath \ "_lt").read[(String, BigDecimal)].map(fv => LtEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_gt").read[(String, BigDecimal)].map(fv => GtEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_lte").read[(String, BigDecimal)].map(fv => LteEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_gte").read[(String, BigDecimal)].map(fv => GteEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_is").read[(String, JsValue)].map(fv => IsEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_startsWith").read[(String, String)].map(fv => StartsWithEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_endsWith").read[(String, String)].map(fv => EndsWithEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_between").read[BetweenEventFilter].map(identity) orElse
      (JsPath \ "_in").read[InEventFilter].map(identity) orElse
      (JsPath \ "_contains").read[(String, String)].map(fv => ContainsEventFilter(fv._1, fv._2)) orElse
      (JsPath \ "_like").read[(String, String)].map(fv => LikeEventFilter(fv._1, fv._2))
}

@Singleton
class FilteredEventProvider @Inject() extends TriggerProvider {
  override val name: String = "FilteredEvent"
  override def apply(config: Configuration): Try[Trigger] = {
    val filter = Json.parse(config.underlying.getValue("filter").render(ConfigRenderOptions.concise())).as[EventFilter]
    Success(new FilteredEvent(filter))
  }
}

class FilteredEvent(eventFilter: EventFilter) extends Trigger {
  override val name: String = "FilteredEvent"

  override def preFilter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity): Boolean =
    try eventFilter(
      Json.obj(
        "requestId"  -> audit.requestId,
        "action"     -> audit.action,
        "mainAction" -> audit.mainAction,
        "objectId"   -> audit.objectId,
        "objectType" -> audit.objectType,
        "details"    -> audit.details,
        "_createdBy" -> audit._createdBy,
        "_updatedBy" -> audit._updatedBy,
        "_createdAt" -> audit._createdAt,
        "_updatedAt" -> audit._updatedAt
      )
    )
    catch {
      case EventFilterOnMissingUser => true
    }

  override def filter(audit: Audit with Entity, context: Option[Entity], organisation: Organisation with Entity, user: Option[User with Entity])(
      implicit graph: Graph
  ): Boolean =
    try super.filter(audit, context, organisation, user) && eventFilter(
      Json.obj(
        "requestId"  -> audit.requestId,
        "action"     -> audit.action,
        "mainAction" -> audit.mainAction,
        "objectId"   -> audit.objectId,
        "objectType" -> audit.objectType,
        "details"    -> audit.details,
        "_createdBy" -> audit._createdBy,
        "_updatedBy" -> audit._updatedBy,
        "_createdAt" -> audit._createdAt,
        "_updatedAt" -> audit._updatedAt,
        "user"       -> user.map(_.login)
      )
    )
    catch {
      case EventFilterOnMissingUser => false
    }
}
