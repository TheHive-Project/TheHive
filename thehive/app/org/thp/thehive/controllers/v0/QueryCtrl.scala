package org.thp.thehive.controllers.v0

import java.util.Date

import scala.util.{Success, Try}

import play.api.Logger
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc.{Action, AnyContent, Results}
import scala.reflect.runtime.{universe => ru}

import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{BaseVertexSteps, Database, PagedResult}
import org.thp.scalligraph.query._

trait QueryableCtrl {
  val entityName: String
  val publicProperties: List[PublicProperty[_, _]]
  val initialQuery: ParamQuery[_]
  val pageQuery: ParamQuery[_]
  val outputQuery: ParamQuery[_]

  def metaProperties[S <: BaseVertexSteps[_, S]: ru.TypeTag]: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[S]
      .property[String]("createdBy")(_.rename("_createdBy").readonly)
      .property[Date]("createdAt")(_.rename("_createdAt").readonly)
      .property[String]("updatedBy")(_.rename("_updatedBy").readonly)
      .property[Date]("updatedAt")(_.rename("_updatedAt").readonly)
      .build
}

class QueryCtrl(entryPoint: EntryPoint, db: Database, ctrl: QueryableCtrl, queryExecutor: QueryExecutor) {
  lazy val logger = Logger(getClass)

  def search: Action[AnyContent] =
    entryPoint(s"search ${ctrl.entityName}")
      .extract("query", searchParser(ctrl.initialQuery.name))
      .authTransaction(db) { implicit request => graph =>
        val query: Query = request.body("query")
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) => Success(resp.withHeaders("X-Total" -> size.toString))
          case _                          => Success(resp)
        }
      }

  def stats: Action[AnyContent] =
    entryPoint("case stats")
      .extract("query", statsParser(ctrl.initialQuery.name))
      .authTransaction(db) { implicit request => graph =>
        val queries: Seq[Query] = request.body("query")
        val results = queries
          .map(query => queryExecutor.execute(query, graph, request.authContext).toJson)
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) => acc ++ o
            case (acc, r) =>
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }

  val outputParamParser: FieldsParser[OutputParam] = FieldsParser[OutputParam]("OutputParam") {
    case (_, o: FObject) =>
      for {
        fromTo <- FieldsParser
          .string
          .optional
          .on("range")
          .apply(o)
          .map {
            case Some("all") => (0L, Long.MaxValue)
            case Some(r) =>
              val Array(offsetStr, endStr, _*) = (r + "-0").split("-", 3)
              val offset: Long                 = Try(Math.max(0, offsetStr.toLong)).getOrElse(0)
              val end: Long                    = Try(endStr.toLong).getOrElse(offset + 10L)
              if (end <= offset) (offset, offset + 10)
              else (offset, end)
            case None => (0L, 10L)
          }
        withSize  <- FieldsParser.boolean.optional.on("withSize")(o)
        withStats <- FieldsParser.boolean.optional.on("withStats")(o)
      } yield OutputParam(fromTo._1, fromTo._2, withSize, withStats)
  }

  val sortParser: FieldsParser[FSeq] = FieldsParser("sort") {
    case (_, FAny(s)) => Good(s)
    case (_, FSeq(s)) => s.validatedBy(FieldsParser.string.apply)
  }.map("sort") { a =>
    val fields = a.collect {
      case s if s(0) == '-'    => FObject(s.drop(1) -> FString("decr"))
      case s if s(0) == '+'    => FObject(s.drop(1) -> FString("incr"))
      case s if s.length() > 0 => FObject(s         -> FString("incr"))
    }
    new FSeq(fields.toList)
  }

  def statsParser(initialStepName: String): FieldsParser[Seq[Query]] =
    statsParser(FObject("_name" -> FString(initialStepName)))

  def statsParser(initialStep: FObject): FieldsParser[Seq[Query]] =
    FieldsParser[Seq[Query]]("query") {
      case (_, obj: FObject) =>
        val filter = FObject.parser.optional(obj.get("query")).map(_.getOrElse(FObject("_any" -> FUndefined)) + ("_name" -> FString("filter")))
        val stats  = FSeq.parser(obj.get("stats")).flatMap(_.values.validatedBy(FObject.parser.apply))
        withGood(filter, stats)(
          (goodFilter, goodStats) => goodStats.map(s => FSeq(initialStep, goodFilter, s + ("_name" -> FString("aggregation"))))
        ).flatMap(s => s.validatedBy(queryExecutor.parser.apply))
    }

  //    def filterParser(initialStepName: String): FieldsParser[Query] =
  //      filterParser(FObject("_name" -> FString(initialStepName)))
  //
  //    def filterParser(initialStep: FObject): FieldsParser[Query] =
  //      FieldsParser[Query]("query") {
  //        case (_, obj: FObject) =>
  //          FObject.parser(obj.get("query")).flatMap { f =>
  //            queryExecutor.parser(FSeq(initialStep, f + ("_name" -> FString("filter"))))
  //          }
  //      }

  /*
Instead of converting Fields which implies double parsing, we could implement a full query parser without using queryExecutor.parser
This is possible and pretty easy because queries are simple.
   */
  def searchParser(initialStepName: String, paged: Boolean = true): FieldsParser[Query] =
    FieldsParser[FObject]
      .optional
      .on("query")
      .map("filter") {
        case Some(filter) => Seq(FObject("_name" -> FString(initialStepName)), filter + ("_name" -> FString("filter")))
        case None         => Seq(FObject("_name" -> FString(initialStepName)))
      }
      .andThen("sort")(sortParser.optional.on("sort")) {
        case (Some(sort), query) => query :+ FObject("_name" -> FString("sort"), "_fields" -> sort)
        case (_, query)          => query
      }
      .andThen("page")(outputParamParser.optional) {
        case (Some(OutputParam(from, to, withSize, withStats)), query) =>
          query :+ FObject(
            "_name"     -> FString("page"),
            "from"      -> FNumber(from),
            "to"        -> FNumber(to),
            "withSize"  -> FBoolean(withSize.getOrElse(paged)),
            "withStats" -> FBoolean(withStats.getOrElse(true))
          )
        case (_, query) =>
          query :+ FObject(
            "_name"     -> FString("page"),
            "from"      -> FNumber(0),
            "to"        -> FNumber(10),
            "withSize"  -> FBoolean(true),
            "withStats" -> FBoolean(true)
          )
      }
      .map("query")(q => FSeq(q.toList))
      .flatMap("query")(queryExecutor.parser)
}
//}
//  def search: Action[AnyContent]
//  def stats: Action[AnyContent]
//}

@Singleton
class QueryCtrlBuilder @Inject()(entryPoint: EntryPoint, db: Database) {

  def apply(ctrl: QueryableCtrl, queryExecutor: QueryExecutor): QueryCtrl =
    new QueryCtrl(entryPoint, db, ctrl, queryExecutor)
}
