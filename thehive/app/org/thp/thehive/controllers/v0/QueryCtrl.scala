package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.apache.tinkerpop.gremlin.structure.Graph
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.Traversal.Unk
import org.thp.thehive.services.th3.TH3Aggregation
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Results}

import scala.reflect.runtime.{universe => ru}
import scala.util.Try

case class IdOrName(idOrName: String)

trait PublicData {
  val entityName: String
  val publicProperties: PublicProperties
  val initialQuery: Query
  val pageQuery: ParamQuery[OutputParam]
  val outputQuery: Query
  val getQuery: ParamQuery[IdOrName]
  val extraQueries: Seq[ParamQuery[_]] = Nil
}
trait QueryCtrl {
  lazy val logger: Logger = Logger(getClass)

  val publicData: PublicData
  val entrypoint: Entrypoint
  val queryExecutor: QueryExecutor
  val db: Database

  val filterQuery: FilterQuery = queryExecutor.filterQuery
  val queryType: ru.Type       = publicData.initialQuery.toType(ru.typeOf[Graph])

  val inputFilterParser: FieldsParser[InputQuery[Unk, Unk]] = filterQuery
    .paramParser(queryType)

  val aggregationParser: FieldsParser[Aggregation] =
    TH3Aggregation.fieldsParser

  val sortParser: FieldsParser[InputSort] = FieldsParser("sort") {
    case (_, FAny(s))    => Good(s)
    case (_, FSeq(s))    => s.validatedBy(FieldsParser.string.apply)
    case (_, FUndefined) => Good(Nil)
  }.map("sort") { a =>
    val fields = a.collect {
      case s if s(0) == '-' => s.tail -> Order.desc
      case s if s(0) == '+' => s.tail -> Order.asc
      case s if s.nonEmpty  => s      -> Order.asc
    }
    new InputSort(fields: _*)
  }

  val outputParamParser: FieldsParser[OutputParam] = FieldsParser[OutputParam]("OutputParam") {
    case (_, o: FObject) =>
      for {
        fromTo <-
          FieldsParser
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
        withStats   <- FieldsParser.boolean.optional.on("nstats")(o)
        withParents <- FieldsParser.int.optional.on("nparent")(o)
      } yield OutputParam(fromTo._1, fromTo._2, withStats.getOrElse(false), withParents.getOrElse(0))
  }

  val statsParser: FieldsParser[Seq[Query]] = FieldsParser[Seq[Query]]("stats") {
    case (_, field) =>
      for {
        maybeInputFilter <- inputFilterParser.optional(field.get("query"))
        filteredQuery =
          maybeInputFilter
            .map(inputFilter => filterQuery.toQuery(inputFilter))
            .fold(publicData.initialQuery)(publicData.initialQuery.andThen)
        aggs <- aggregationParser.sequence(field.get("stats"))
      } yield aggs.map(a => filteredQuery andThen new AggregationQuery(db, queryExecutor.publicProperties, filterQuery).toQuery(a))
  }

  def searchParser(initialQuery: Query = publicData.initialQuery): FieldsParser[Query] =
    FieldsParser[Query]("search") {
      case (_, field) =>
        for {
          maybeInputFilter <- inputFilterParser.optional(field.get("query"))
          filteredQuery =
            maybeInputFilter
              .map(inputFilter => filterQuery.toQuery(inputFilter))
              .fold(initialQuery)(initialQuery.andThen)
          inputSort <- sortParser(field.get("sort"))
          sortedQuery = filteredQuery andThen new SortQuery(db, queryExecutor.publicProperties).toQuery(inputSort)
          outputParam <- outputParamParser.optional(field).map(_.getOrElse(OutputParam(0, 10, withStats = false, withParents = 0)))
          outputQuery = publicData.pageQuery.toQuery(outputParam)
        } yield sortedQuery andThen outputQuery
    }

  def search: Action[AnyContent] =
    entrypoint(s"search ${publicData.entityName}")
      .extract("query", searchParser())
      .auth { implicit request =>
        val query: Query = request.body("query")
        queryExecutor.execute(query, request)
      }

  def stats: Action[AnyContent] =
    entrypoint(s"${publicData.entityName} stats")
      .extract("query", statsParser)
      .authRoTransaction(db) { implicit request => graph =>
        val queries: Seq[Query] = request.body("query")
        queries
          .toTry(query => queryExecutor.execute(query, graph, request.authContext))
          .map { outputs =>
            val results = outputs.map(_.toJson).foldLeft(JsObject.empty) {
              case (acc, o: JsObject) => acc deepMerge o
              case (acc, r) =>
                logger.warn(s"Invalid stats result: $r")
                acc
            }
            Results.Ok(results)
          }
      }
}
