package org.thp.thehive.controllers.v1

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.scalactic.{One, Or}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, IndexType}
import org.thp.scalligraph.query.{Aggregation, PublicProperty, Query}
import org.thp.scalligraph.traversal.{Converter, GenericBySelector, Graph, Traversal}
import org.thp.scalligraph.{AttributeCheckingError, InvalidFormatAttributeError, NotFoundError}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, JsString}
import play.api.mvc.{Action, AnyContent, Results}

import java.time.temporal.ChronoUnit
import java.util.{Date, List => JList}
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

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
  val chronoUnitParser: FieldsParser[ChronoUnit] = FieldsParser[ChronoUnit]("chronoUnit") {
    case (_, f @ FString(value)) =>
      Or.from(
        Try(ChronoUnit.valueOf(value)).toOption,
        One(InvalidFormatAttributeError("_unit", "chronoUnit", ChronoUnit.values.toSet.map((_: ChronoUnit).toString), f))
      )
  }

  def timeChart: Action[AnyContent] =
    entrypoint("time chart")
      .extract("model", FieldsParser[String].on("model"))
      .extract("dateField", FieldsParser[String].on("dateField"))
      .extract("interval", FieldsParser[Long].on("interval"))
      .extract("unit", chronoUnitParser.on("unit"))
      .extract("subAggs", FieldsParser[Field].on("subAggs"))
      .authTransaction(db) { implicit request => implicit graph =>
        val model: String     = request.body("model")
        val dateField: String = request.body("dateField")
        val count: Long       = request.body("interval")
        val unit: ChronoUnit  = request.body("unit")
        val interval          = unit.getDuration.toMillis * count
        for {
          tpe <- types.get(model).fold[Try[ru.Type]](Failure(NotFoundError(s"Model $model not found")))(Success(_))
          subAggs <-
            Aggregation
              .fieldsParser(queryExecutor.filterQuery.paramParser(tpe))
              .sequence
              .on("subAggs")(request.body("subAggs"))
              .badMap(AttributeCheckingError.apply(_))
              .toTry
          initQuery <-
            queryExecutor
              .queries
              .find(q => q.name == s"list$model" && q.checkFrom(graphType))
              .fold[Try[Query]](Failure(NotFoundError(s"Initial query for iterating $model is not found")))(q => Success(q.asInstanceOf[Query]))
          property <-
            queryExecutor
              .publicProperties
              .get(dateField, tpe)
              .filter(p => p.mapping.domainTypeClass == classOf[Date] && p.indexType != IndexType.none)
              .fold[Try[PublicProperty]](Failure(NotFoundError(s"Property $dateField not found (or it is not date, or it is not indexed)")))(
                Success(_)
              ) // remove basic and unique index type ?
        } yield getSeries(tpe, property, initQuery, interval, subAggs).fold(Results.Ok(JsArray.empty)) { src =>
          Results.Ok.chunked(src.map(_.toString).intersperse("[", ",", "]"), Some("application/json"))
        }
      }

  private def getSeries(tpe: ru.Type, property: PublicProperty, initQuery: Query, interval: Long, subAggs: Seq[Aggregation])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Option[Source[JsObject, NotUsed]] =
    for {
      minDate <- property.select(FPath.empty, initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk], authContext).min.headOption
      maxDate <- property.select(FPath.empty, initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk], authContext).max.headOption
    } yield db.source { graph =>
      val min = (minDate.asInstanceOf[Date].getTime / interval) * interval
      val max = Math.ceil(maxDate.asInstanceOf[Date].getTime.toDouble / interval).toLong * interval
      (min until max).by(interval).iterator.flatMap { d =>
        val subAggProjection = subAggs.map {
          agg => (s: GenericBySelector[Seq[Traversal.UnkD], JList[Traversal.UnkG], Converter.CList[Traversal.UnkD, Traversal.UnkG, Converter[
            Traversal.UnkD,
            Traversal.UnkG
          ]]]) =>
            s.by(t => agg.getTraversal(queryExecutor.publicProperties, tpe, t.unfold, authContext).castDomain[Output[_]])
        }

        property
          .filter(
            FPath.empty,
            initQuery((), graphType, graph, authContext).asInstanceOf[Traversal.Unk],
            authContext,
            P.between(new Date(d), new Date(d + interval))
          )
          .fold
          .flatProject(subAggProjection: _*)
          .domainMap { aggResult =>
            val outputs = aggResult.asInstanceOf[Seq[Output[_]]]
            val json = outputs.map(_.toJson).foldLeft(JsObject.empty) {
              case (acc, jsObject: JsObject) => acc ++ jsObject
              case (acc, r) =>
                Aggregation.logger.warn(s"Invalid stats result: $r")
                acc
            }
            Output(outputs.map(_.toValue), json)
          }
          .headOption
          .map(_.toJson.as[JsObject] + ("_key" -> JsString(d.toString)))
      }
    }
}
