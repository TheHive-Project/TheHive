package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.scalactic.Accumulation.withGood
import org.scalactic._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, IndexType}
import org.thp.scalligraph.query.{Aggregation, PublicProperty, Query}
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{AttributeError, UnsupportedAttributeError}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsNumber, JsObject, JsValue}
import play.api.mvc.{Action, AnyContent, Results}

import java.time.temporal.ChronoUnit
import java.util.Date
import scala.reflect.runtime.{universe => ru}
import scala.util.Success

class ChartCtrl(
    entrypoint: Entrypoint,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    db: Database,
    queryExecutor: TheHiveQueryExecutor
) extends TheHiveOps {
  val types = Map(
    "Alert"          -> ru.typeOf[Traversal.V[Alert]],
    "Audit"          -> ru.typeOf[Traversal.V[Audit]],
    "Case"           -> ru.typeOf[Traversal.V[Case]],
    "CaseTemplate"   -> ru.typeOf[Traversal.V[CaseTemplate]],
    "CustomField"    -> ru.typeOf[Traversal.V[CustomField]],
    "Dashboard"      -> ru.typeOf[Traversal.V[Dashboard]],
    "Log"            -> ru.typeOf[Traversal.V[Log]],
    "Observable"     -> ru.typeOf[Traversal.V[Observable]],
    "ObservableType" -> ru.typeOf[Traversal.V[ObservableType]],
    "Organisation"   -> ru.typeOf[Traversal.V[Organisation]],
    "Pattern"        -> ru.typeOf[Traversal.V[Pattern]],
    "Procedure"      -> ru.typeOf[Traversal.V[Procedure]],
    "Profile"        -> ru.typeOf[Traversal.V[Profile]],
    "Share"          -> ru.typeOf[Traversal.V[Share]],
    "Tag"            -> ru.typeOf[Traversal.V[Tag]],
    "Task"           -> ru.typeOf[Traversal.V[Task]],
    "User"           -> ru.typeOf[Traversal.V[User]],
    "Taxonomy"       -> ru.typeOf[Traversal.V[Taxonomy]]
  )

  lazy val graphType: ru.Type = ru.typeOf[Graph]

  case class Series(
      name: String,
      tpe: ru.Type,
      property: PublicProperty,
      initQuery: Query,
      agg: Aggregation,
      min: Option[Long],
      max: Option[Long]
  ) {
    def withRange(implicit graph: Graph, authContext: AuthContext): Option[Series] =
      for {
        minDate <- property.select(FPath.empty, initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk], authContext).min.headOption
        maxDate <- property.select(FPath.empty, initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk], authContext).max.headOption
      } yield copy(min = Some(minDate.asInstanceOf[Date].getTime), max = Some(maxDate.asInstanceOf[Date].getTime))

    def data(fromDate: Long, toDate: Long)(implicit graph: Graph, authContext: AuthContext): Option[JsValue] =
      if (min.exists(toDate < _) || max.exists(fromDate > _) || fromDate > toDate) None
      else
        agg
          .getTraversal(
            queryExecutor.publicProperties,
            tpe,
            property
              .filter(
                FPath.empty,
                initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk],
                authContext,
                P.between(new Date(fromDate), new Date(toDate))
              ),
            authContext
          )
          .headOption
  }

  object Series {
    def parse(name: String, model: String, dateField: String, agg: Field): Series Or Every[AttributeError] =
      for {
        tpe <- types.get(model).fold[ru.Type Or Every[AttributeError]](Bad(One(UnsupportedAttributeError("model"))))(Good(_))
        agg <-
          Aggregation
            .fieldsParser(queryExecutor.filterQuery.paramParser(tpe))(agg)
        initQuery <-
          queryExecutor
            .queries
            .find(q => q.name == s"list$model" && q.checkFrom(graphType))
            .fold[Query Or Every[AttributeError]](Bad(One(UnsupportedAttributeError("model"))))(q => Good(q.asInstanceOf[Query]))
        property <-
          queryExecutor
            .publicProperties
            .get(dateField, tpe)
            .filter(p => p.mapping.domainTypeClass == classOf[Date] && p.indexType != IndexType.none)
            .fold[PublicProperty Or Every[AttributeError]](Bad(One(UnsupportedAttributeError("dateField"))))(
              Good(_)
            ) // remove basic and unique index type ?
      } yield Series(
        name,
        tpe,
        property,
        initQuery,
        agg,
        None,
        None
      )

    def fieldsParser: FieldsParser[Series] =
      FieldsParser("Series") {
        case (_, field) =>
          withGood(
            FieldsParser[String].on("name")(field),
            FieldsParser[String].on("model")(field),
            FieldsParser[String].on("dateField")(field)
          )((name, model, dataField) => parse(name, model, dataField, field.get("agg"))).flatMap(identity[Series Or Every[AttributeError]])
      }
  }

  def timeChart: Action[AnyContent] =
    entrypoint("time chart")
      .extract("interval", Aggregation.mergedIntervalParser.on("interval"))
      .extract("from", FieldsParser[Date].on("from"))
      .extract("to", FieldsParser[Date].on("to"))
      .extract("aggs", Series.fieldsParser.sequence.on("aggs"))
      .authTransaction(db) { implicit request => graph =>
        val (count: Long, unit: ChronoUnit) = request.body("interval")
        val interval                        = unit.getDuration.toMillis * count
        val series: Seq[Series]             = request.body("aggs")
        val from: Date                      = request.body("from")
        val to: Date                        = request.body("to")

        val rangedSeries = series.flatMap(_.withRange(graph, request))
        val src = db.source { implicit graph =>
          (from.getTime until to.getTime).by(interval).iterator.map { date =>
            val seriesData = rangedSeries.flatMap { s =>
              s.data(date, date + interval)
                .map(s.name -> _)
            }
            JsObject(seriesData :+ ("_key" -> JsNumber(date)))
          }
        }
        Success(Results.Ok.chunked(src.map(_.toString).intersperse("[", ",", "]"), Some("application/json")))
      }
}
