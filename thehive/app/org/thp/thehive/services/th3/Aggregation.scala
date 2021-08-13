package org.thp.thehive.services.th3

import org.scalactic.Accumulation._
import org.scalactic._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.query.{Aggregation, InputFilter, PublicProperties}
import org.thp.scalligraph.traversal.Traversal.Unk
import org.thp.scalligraph.{InvalidFormatAttributeError, query => scalligraph}
import play.api.libs.json._

import java.time.temporal.ChronoUnit
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

  def aggregationFieldParser(
      filterParser: FieldsParser[InputFilter]
  ): PartialFunction[String, FieldsParser[Aggregation]] = {
    case "field" =>
      FieldsParser("FieldAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            FieldsParser.string.sequence.on("_order")(field).orElse(FieldsParser.string.on("_order").map("order")(Seq(_))(field)),
            FieldsParser.long.optional.on("_size")(field),
            fieldsParser(filterParser).sequence.on("_select")(field),
            filterParser.optional.on("_query")(field)
          )((aggName, fieldName, order, size, subAgg, filter) => new scalligraph.FieldAggregation(aggName, fieldName, order, size, subAgg, filter))
      }
    case "count" =>
      FieldsParser("CountAggregation") {
        case (_, field) =>
          withGood(FieldsParser.string.optional.on("_name")(field), filterParser.optional.on("_query")(field))((aggName, filter) =>
            new AggCount(aggName, filter)
          )
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
            fieldsParser(filterParser).sequence.on("_select")(field),
            filterParser.optional.on("_query")(field)
          ) { (aggName, fieldNames, intervalUnit, subAgg, filter) =>
            if (fieldNames.lengthCompare(1) > 0)
              Aggregation
                .logger
                .warn(s"Only one field is supported for time aggregation (aggregation $aggName, ${fieldNames.tail.mkString(",")} are ignored)")
            new scalligraph.TimeAggregation(aggName, fieldNames.head, intervalUnit._1, intervalUnit._2, subAgg, filter)
          }
      }
    case "avg" =>
      FieldsParser("AvgAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            filterParser.optional.on("_query")(field)
          )((aggName, fieldName, filter) => new AggAvg(aggName, fieldName, filter))
      }
    case "min" =>
      FieldsParser("MinAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            filterParser.optional.on("_query")(field)
          )((aggName, fieldName, filter) => new AggMin(aggName, fieldName, filter))
      }
    case "max" =>
      FieldsParser("MaxAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            filterParser.optional.on("_query")(field)
          )((aggName, fieldName, filter) => new AggMax(aggName, fieldName, filter))
      }
    case "sum" =>
      FieldsParser("SumAggregation") {
        case (_, field) =>
          withGood(
            FieldsParser.string.optional.on("_name")(field),
            FieldsParser.string.on("_field")(field),
            filterParser.optional.on("_query")(field)
          )((aggName, fieldName, filter) => new AggSum(aggName, fieldName, filter))
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

  def fieldsParser(filterParser: FieldsParser[InputFilter]): FieldsParser[Aggregation] =
    FieldsParser("aggregation") {
      case (_, AggObj(name, field)) => aggregationFieldParser(filterParser)(name)(field)
    }
}

class AggSum(aggName: Option[String], fieldName: String, filter: Option[InputFilter]) extends scalligraph.AggSum(aggName, fieldName, filter) {}

class AggAvg(aggName: Option[String], fieldName: String, filter: Option[InputFilter]) extends scalligraph.AggAvg(aggName, fieldName, filter) {}

class AggMin(aggName: Option[String], fieldName: String, filter: Option[InputFilter]) extends scalligraph.AggMin(aggName, fieldName, filter) {
  override def apply(publicProperties: PublicProperties, traversalType: ru.Type, traversal: Unk, authContext: AuthContext): Output =
    Output(
      getTraversal(publicProperties, traversalType, traversal, authContext)
        .headOption
        .fold[JsValue](JsNull)(v => Json.obj(name -> v))
    )

}

class AggMax(aggName: Option[String], fieldName: String, filter: Option[InputFilter]) extends scalligraph.AggMax(aggName, fieldName, filter) {
  override def apply(publicProperties: PublicProperties, traversalType: ru.Type, traversal: Unk, authContext: AuthContext): Output =
    Output(
      getTraversal(publicProperties, traversalType, traversal, authContext)
        .headOption
        .fold[JsValue](JsNull)(v => Json.obj(name -> v))
    )
}

class AggCount(aggName: Option[String], filter: Option[InputFilter]) extends scalligraph.AggCount(aggName, filter) {
  override def apply(publicProperties: PublicProperties, traversalType: ru.Type, traversal: Unk, authContext: AuthContext): Output =
    Output(
      getTraversal(publicProperties, traversalType, traversal, authContext)
        .headOption
        .fold[JsValue](JsNull)(v => Json.obj(name -> v))
    )
}
