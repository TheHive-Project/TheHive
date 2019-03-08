package org.thp.thehive.controllers.v0
import scala.util.Try

import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.query.{Query, QueryExecutor}

trait QueryCtrl {
  val queryExecutor: QueryExecutor
  val rangeParser: FieldsParser[(Long, Long)] = FieldsParser.string.map("range") {
    case "all" ⇒ (0, Long.MaxValue)
    case r ⇒
      val Array(offsetStr, endStr, _*) = (r + "-0").split("-", 3)
      val offset: Long                 = Try(Math.max(0, offsetStr.toLong)).getOrElse(0)
      val end: Long                    = Try(endStr.toLong).getOrElse(offset + 10L)
      if (end <= offset) (offset, offset + 10)
      else (offset, end)
  }
  val sortParser: FieldsParser[FSeq] = FieldsParser("sort-1") {
    case (_, FAny(s)) ⇒ Good(s)
    case (_, FSeq(s)) ⇒ s.validatedBy(FieldsParser.string.apply)
  }.map("sort-2") { a ⇒
    val fields = a.collect {
      case s if s(0) == '-'    ⇒ FObject(s.drop(1) → FString("decr"))
      case s if s(0) == '+'    ⇒ FObject(s.drop(1) → FString("incr"))
      case s if s.length() > 0 ⇒ FObject(s         → FString("incr"))
    }
    new FSeq(fields.toList)
  }

  def statsParser(initialStepName: String): FieldsParser[Seq[Query]] =
    statsParser(FObject("_name" → FString(initialStepName)))

  def statsParser(initialStep: FObject): FieldsParser[Seq[Query]] =
    FieldsParser[Seq[Query]]("query") {
      case (_, obj: FObject) ⇒
        val filter = FObject.parser(obj.get("query")).map(_ + ("_name" → FString("filter")))
        val stats  = FSeq.parser(obj.get("stats")).flatMap(_.values.validatedBy(FObject.parser.apply))
        withGood(filter, stats)((goodFilter, goodStats) ⇒ goodStats.map(s ⇒ FSeq(initialStep, goodFilter, s + ("_name" → FString("aggregation")))))
          .flatMap(s ⇒ s.validatedBy(queryExecutor.parser.apply))
    }

  def filterParser(initialStepName: String): FieldsParser[Query] =
    filterParser(FObject("_name" → FString(initialStepName)))

  def filterParser(initialStep: FObject): FieldsParser[Query] =
    FieldsParser[Query]("query") {
      case (_, obj: FObject) ⇒
        FObject.parser(obj.get("query")).flatMap { f ⇒
          queryExecutor.parser(FSeq(initialStep, f + ("_name" → FString("filter"))))
        }
    }

  def searchParser(initialStepName: String): FieldsParser[Query] =
    searchParser(FObject("_name" → FString(initialStepName)))

  def searchParser(initialStep: FObject): FieldsParser[Query] = {
    val queryParser = FieldsParser[FObject].on("query").map("filter")(filter ⇒ Seq(initialStep, filter + ("_name" → FString("filter"))))
//    val sortParser  = FieldsParser[FObject].optional.on("sort")
    queryParser
      .andThen("sort")(sortParser.optional.on("sort")) {
        case (Some(sort), query) ⇒ query :+ FObject("_name" → FString("sort"), "_fields" → sort)
        case (_, query)          ⇒ query
      }
      .andThen("range")(rangeParser.optional.on("range")) {
        case (Some((from, to)), query) ⇒ query :+ FObject("_name" → FString("range"), "from" → FNumber(from), "to" → FNumber(to))
        case (_, query)                ⇒ query :+ FObject("_name" → FString("range"), "from" → FNumber(0), "to"    → FNumber(10))
      }
      .map("query")(_ :+ FObject("_name" → FString("toList")))
      .map("query")(q ⇒ FSeq(q.toList))
      .flatMap("query")(queryExecutor.parser)
  }
}
