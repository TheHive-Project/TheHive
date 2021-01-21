package org.thp.thehive.services.th3

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.query.{Aggregation, PublicProperties}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.{BadRequestError, InvalidFormatAttributeError}
import play.api.Logger
import play.api.libs.json.{JsNull, JsNumber, JsObject, Json}

import java.lang.{Long => JLong}
import java.time.temporal.ChronoUnit
import java.util.{Calendar, Date, List => JList}
import scala.reflect.runtime.{universe => ru}
import scala.util.Try
import scala.util.matching.Regex

object TH3Aggregation {

  object AggObj {
    def unapply(field: Field): Option[(String, FObject)] =
      field match {
        case f: FObject =>
          f.get("_agg") match {
            case FString(name) => Some(name -> (f - "_agg"))
            case _             => None
          }
        case _ => None
      }
  }

  val intervalParser: FieldsParser[(Long, ChronoUnit)] = FieldsParser[(Long, ChronoUnit)]("interval") {
    case (_, f) =>
      withGood(
        FieldsParser.long.optional.on("_interval")(f),
        FieldsParser[ChronoUnit]("chronoUnit") {
          case (_, f @ FString(value)) =>
            Or.from(
              Try(ChronoUnit.valueOf(value)).toOption,
              One(InvalidFormatAttributeError("_unit", "chronoUnit", ChronoUnit.values.toSet.map((_: ChronoUnit).toString), f))
            )
        }.on("_unit")(f)
      )((i, u) => i.getOrElse(0L) -> u)
  }

  val intervalRegex: Regex = "(\\d+)([smhdwMy])".r

  val mergedIntervalParser: FieldsParser[(Long, ChronoUnit)] = FieldsParser[(Long, ChronoUnit)]("interval") {
    case (_, FString(intervalRegex(interval, unit))) =>
      Good(unit match {
        case "s" => interval.toLong -> ChronoUnit.SECONDS
        case "m" => interval.toLong -> ChronoUnit.MINUTES
        case "h" => interval.toLong -> ChronoUnit.HOURS
        case "d" => interval.toLong -> ChronoUnit.DAYS
        case "w" => interval.toLong -> ChronoUnit.WEEKS
        case "M" => interval.toLong -> ChronoUnit.MONTHS
        case "y" => interval.toLong -> ChronoUnit.YEARS
      })
  }

  def aggregationFieldParser: PartialFunction[String, FieldsParser[Aggregation]] = {
    case "field" =>
      FieldsParser("FieldAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            FieldsParser.string.sequence.on("_order")(field).orElse(FieldsParser.string.on("_order").map("order")(Seq(_))(field)),
            FieldsParser.long.optional.on("_size")(field),
            fieldsParser.sequence.on("_select")(field)
          )((aggName, fieldName, order, size, subAgg) => FieldAggregation(aggName, fieldName, order, size, subAgg))
      }
    case "count" =>
      FieldsParser("CountAggregation") {
        case (_, field) => FieldsParser.string.optional.on("_name")(field).map(aggName => AggCount(aggName))
      }
    case "time" =>
      FieldsParser("TimeAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser
              .string
              .sequence
              .on("_fields")(field)
              .orElse(FieldsParser.string.on("_fields")(field).map(Seq(_))), //.map("toSeq")(f => Good(Seq(f)))),
            mergedIntervalParser.on("_interval").orElse(intervalParser)(field),
            fieldsParser.sequence.on("_select")(field)
          ) { (aggName, fieldNames, intervalUnit, subAgg) =>
            if (fieldNames.lengthCompare(1) > 0)
              logger.warn(s"Only one field is supported for time aggregation (aggregation $aggName, ${fieldNames.tail.mkString(",")} are ignored)")
            TimeAggregation(aggName, fieldNames.head, intervalUnit._1, intervalUnit._2, subAgg)
          }
      }
    case "avg" =>
      FieldsParser("AvgAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field)
          )((aggName, fieldName) => AggAvg(aggName, fieldName))
      }
    case "min" =>
      FieldsParser("MinAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field)
          )((aggName, fieldName) => AggMin(aggName, fieldName))
      }
    case "max" =>
      FieldsParser("MaxAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field)
          )((aggName, fieldName) => AggMax(aggName, fieldName))
      }
    case "sum" =>
      FieldsParser("SumAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field)
          )((aggName, fieldName) => AggSum(aggName, fieldName))
      }
    case other =>
      new FieldsParser[Aggregation](
        "unknownAttribute",
        Set.empty,
        {
          case (path, _) =>
            Bad(One(InvalidFormatAttributeError(path.toString, "string", Set("field", "time", "count", "avg", "min", "max"), FString(other))))
        }
      )
  }

  implicit val fieldsParser: FieldsParser[Aggregation] = FieldsParser("aggregation") {
    case (_, AggObj(name, field)) => aggregationFieldParser(name)(field)
  }
}

case class AggSum(aggName: Option[String], fieldName: String) extends Aggregation(aggName.getOrElse(s"sum_$fieldName")) {
  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val fieldPath = FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    traversal.coalesce(
      t =>
        property
          .select(fieldPath, t, authContext)
          .sum
          .domainMap(sum => Output(Json.obj(name -> JsNumber(BigDecimal(sum.toString)))))
          .castDomain[Output[_]],
      Output(Json.obj(name -> JsNull))
    )
  }
}
case class AggAvg(aggName: Option[String], fieldName: String) extends Aggregation(aggName.getOrElse(s"sum_$fieldName")) {
  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val fieldPath = if (fieldName.startsWith("computed")) FPathElem(fieldName) else FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    traversal.coalesce(
      t =>
        property
          .select(fieldPath, t, authContext)
          .mean
          .domainMap(avg => Output(Json.obj(name -> avg.asInstanceOf[Double]))),
      Output(Json.obj(name -> JsNull))
    )
  }
}

case class AggMin(aggName: Option[String], fieldName: String) extends Aggregation(aggName.getOrElse(s"min_$fieldName")) {
  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val fieldPath = FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    traversal.coalesce(
      t =>
        property
          .select(fieldPath, t, authContext)
          .min
          .domainMap(min => Output(Json.obj(name -> property.toJson(min)))),
      Output(Json.obj(name -> JsNull))
    )
  }
}

case class AggMax(aggName: Option[String], fieldName: String) extends Aggregation(aggName.getOrElse(s"max_$fieldName")) {
  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val fieldPath = FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    traversal.coalesce(
      t =>
        property
          .select(fieldPath, t, authContext)
          .max
          .domainMap(max => Output(Json.obj(name -> property.toJson(max)))),
      Output(Json.obj(name -> JsNull))
    )
  }
}

case class AggCount(aggName: Option[String]) extends Aggregation(aggName.getOrElse("count")) {
  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] =
    traversal
      .count
      .domainMap(count => Output(Json.obj(name -> count)))
      .castDomain[Output[_]]
}

//case class AggTop[T](fieldName: String) extends AggFunction[T](s"top_$fieldName")

case class FieldAggregation(
    aggName: Option[String],
    fieldName: String,
    orders: Seq[String],
    size: Option[Long],
    subAggs: Seq[Aggregation]
) extends Aggregation(aggName.getOrElse(s"field_$fieldName")) {
  lazy val logger: Logger = Logger(getClass)

  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val label     = StepLabel[Traversal.UnkD, Traversal.UnkG, Converter[Traversal.UnkD, Traversal.UnkG]]
    val fieldPath = FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    val groupedVertices = property.select(fieldPath, traversal.as(label), authContext).group(_.by, _.by(_.select(label).fold)).unfold
    val sortedAndGroupedVertex = orders
      .map {
        case order if order.headOption.contains('-') => order.tail -> Order.desc
        case order if order.headOption.contains('+') => order.tail -> Order.asc
        case order                                   => order      -> Order.asc
      }
      .foldLeft(groupedVertices) {
        case (acc, (field, order)) if field == fieldName                    => acc.sort(_.by(_.selectKeys, order))
        case (acc, (field, order)) if field == "count" || field == "_count" => acc.sort(_.by(_.selectValues.localCount, order))
        case (acc, (field, _)) =>
          logger.warn(s"In field aggregation you can only sort by the field ($fieldName) or by count, not by $field")
          acc
      }
    val sizedSortedAndGroupedVertex = size.fold(sortedAndGroupedVertex)(sortedAndGroupedVertex.limit)
    val subAggProjection = subAggs.map {
      agg => (s: GenericBySelector[Seq[Traversal.UnkD], JList[Traversal.UnkG], Converter.CList[Traversal.UnkD, Traversal.UnkG, Converter[
        Traversal.UnkD,
        Traversal.UnkG
      ]]]) =>
        s.by(t => agg.getTraversal(publicProperties, traversalType, t.unfold, authContext).castDomain[Output[_]])
    }

    sizedSortedAndGroupedVertex
      .project(
        _.by(_.selectKeys)
          .by(
            _.selectValues
              .flatProject(subAggProjection: _*)
              .domainMap { aggResult =>
                Output(
                  aggResult
                    .asInstanceOf[Seq[Output[JsObject]]]
                    .map(_.toValue)
                    .reduceOption(_ deepMerge _)
                    .getOrElse(JsObject.empty)
                )
              }
          )
      )
      .fold
      .domainMap(kvs => Output(JsObject(kvs.map(kv => kv._1.toString -> kv._2.toJson))))
      .castDomain[Output[_]]
  }
}

case class TimeAggregation(
    aggName: Option[String],
    fieldName: String,
    interval: Long,
    unit: ChronoUnit,
    subAggs: Seq[Aggregation]
) extends Aggregation(aggName.getOrElse(fieldName)) {
  val calendar: Calendar = Calendar.getInstance()

  def dateToKey(date: Date): Long =
    unit match {
      case ChronoUnit.WEEKS =>
        calendar.setTime(date)
        val year = calendar.get(Calendar.YEAR)
        val week = (calendar.get(Calendar.WEEK_OF_YEAR) / interval) * interval
        calendar.setTimeInMillis(0)
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.WEEK_OF_YEAR, week.toInt)
        calendar.getTimeInMillis

      case ChronoUnit.MONTHS =>
        calendar.setTime(date)
        val year  = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) / interval) * interval
        calendar.setTimeInMillis(0)
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month.toInt)
        calendar.getTimeInMillis

      case ChronoUnit.YEARS =>
        calendar.setTime(date)
        val year = (calendar.get(Calendar.YEAR) / interval) * interval
        calendar.setTimeInMillis(0)
        calendar.set(Calendar.YEAR, year.toInt)
        calendar.getTimeInMillis

      case other =>
        val duration = other.getDuration.toMillis * interval
        (date.getTime / duration) * duration
    }

  def keyToDate(key: Long): Date = new Date(key)

  override def getTraversal(
      publicProperties: PublicProperties,
      traversalType: ru.Type,
      traversal: Traversal.Unk,
      authContext: AuthContext
  ): Traversal.Domain[Output[_]] = {
    val fieldPath = FPath(fieldName)
    val property = publicProperties
      .get[Traversal.UnkD, Traversal.UnkDU](fieldPath, traversalType)
      .getOrElse(throw BadRequestError(s"Property $fieldName for type $traversalType not found"))
    val label = StepLabel[Traversal.UnkD, Traversal.UnkG, Converter[Traversal.UnkD, Traversal.UnkG]]
    val groupedVertex = property
      .select(fieldPath, traversal.as(label), authContext)
      .cast[Date, Date]
      .graphMap[Long, JLong, Converter[Long, JLong]](dateToKey, Converter.long)
      .group(_.by, _.by(_.select(label).fold))
      .unfold
    val subAggProjection = subAggs.map {
      agg => (s: GenericBySelector[
        Seq[Traversal.UnkD],
        JList[Traversal.UnkG],
        Converter.CList[Traversal.UnkD, Traversal.UnkG, Converter[Traversal.UnkD, Traversal.UnkG]]
      ]) =>
        s.by(t => agg.getTraversal(publicProperties, traversalType, t.unfold, authContext).castDomain[Output[_]])
    }

    groupedVertex
      .project(
        _.by(_.selectKeys)
          .by(
            _.selectValues
              .flatProject(subAggProjection: _*)
              .domainMap { aggResult =>
                Output(
                  aggResult
                    .asInstanceOf[Seq[Output[JsObject]]]
                    .map(_.toValue)
                    .reduceOption(_ deepMerge _)
                    .getOrElse(JsObject.empty)
                )
              }
          )
      )
      .fold
      .domainMap(kvs => Output(JsObject(kvs.map(kv => kv._1.toString -> Json.obj(name -> kv._2.toJson)))))
      .castDomain[Output[_]]
  }
}
