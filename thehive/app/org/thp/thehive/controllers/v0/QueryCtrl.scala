package org.thp.thehive.controllers.v0

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.scalactic.Accumulation._
import org.scalactic.Good
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{BaseVertexSteps, Database, PagedResult, UniMapping}
import org.thp.scalligraph.query._
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc.{Action, AnyContent, Results}

import scala.reflect.runtime.{universe => ru}
import scala.util.{Success, Try}

trait QueryableCtrl {
  val entityName: String
  val publicProperties: List[PublicProperty[_, _]]
  val initialQuery: Query
  val pageQuery: ParamQuery[OutputParam]
  val outputQuery: Query

  def metaProperties[S <: BaseVertexSteps[_, S]: ru.TypeTag]: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[S]
      .property("createdBy", UniMapping.string)(_.rename("_createdBy").readonly)
      .property("createdAt", UniMapping.date)(_.rename("_createdAt").readonly)
      .property("updatedBy", UniMapping.string)(_.rename("_updatedBy").readonly)
      .property("updatedAt", UniMapping.date)(_.rename("_updatedAt").readonly)
      .build
}

class QueryCtrl(entryPoint: EntryPoint, db: Database, ctrl: QueryableCtrl, queryExecutor: QueryExecutor) {
  lazy val logger = Logger(getClass)

  val publicProperties: List[PublicProperty[_, _]] = queryExecutor.publicProperties
  val filterQuery: FilterQuery                     = new FilterQuery(db, publicProperties)
  val queryType: ru.Type                           = ctrl.initialQuery.toType(ru.typeOf[Graph])

  val inputFilterParser: FieldsParser[InputFilter] = queryExecutor
    .filterQuery
    .paramParser(queryType, publicProperties)

  val aggregationParser: FieldsParser[GroupAggregation[_, _]] = queryExecutor
    .aggregationQuery
    .paramParser(queryType, publicProperties)

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
        withStats <- FieldsParser.boolean.optional.on("nstats")(o)
      } yield OutputParam(fromTo._1, fromTo._2, withStats.getOrElse(false))
  }

  val statsParser: FieldsParser[Seq[Query]] = FieldsParser[Seq[Query]]("stats") {
    case (_, field) =>
      for {
        maybeInputFilter <- inputFilterParser.optional(field.get("query"))
        filteredQuery = maybeInputFilter
          .map(inputFilter => filterQuery.toQuery(inputFilter))
          .fold(ctrl.initialQuery)(ctrl.initialQuery.andThen)
        groupAggs <- aggregationParser.sequence(field.get("stats"))
      } yield groupAggs.map(a => filteredQuery andThen new AggregationQuery(publicProperties).toQuery(a))
  }

  val searchParser: FieldsParser[Query] = FieldsParser[Query]("search") {
    case (_, field) =>
      for {
        maybeInputFilter <- inputFilterParser.optional(field.get("query"))
        filteredQuery = maybeInputFilter
          .map(inputFilter => filterQuery.toQuery(inputFilter))
          .fold(ctrl.initialQuery)(ctrl.initialQuery.andThen)
        inputSort <- sortParser(field.get("sort"))
        sortedQuery = filteredQuery andThen new SortQuery(db, publicProperties).toQuery(inputSort)
        outputParam <- outputParamParser.optional(field).map(_.getOrElse(OutputParam(0, 10, withStats = false)))
        outputQuery = ctrl.pageQuery.toQuery(outputParam)
      } yield sortedQuery andThen outputQuery
  }

  def search: Action[AnyContent] =
    entryPoint(s"search ${ctrl.entityName}")
      .extract("query", searchParser)
      .authRoTransaction(db) { implicit request => graph =>
        val query: Query = request.body("query")
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) => Success(resp.withHeaders("X-Total" -> size.toString))
          case _                          => Success(resp)
        }
      }

  def stats: Action[AnyContent] =
    entryPoint(s"${ctrl.entityName} stats")
      .extract("query", statsParser)
      .authRoTransaction(db) { implicit request => graph =>
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
}

@Singleton
class QueryCtrlBuilder @Inject()(entryPoint: EntryPoint, db: Database) {

  def apply(ctrl: QueryableCtrl, queryExecutor: QueryExecutor): QueryCtrl =
    new QueryCtrl(entryPoint, db, ctrl, queryExecutor)
}
