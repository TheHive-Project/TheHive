package org.thp.thehive.controllers.v0

import java.util.Date

import gremlin.scala.{By, Graph, GremlinScala, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputObservable, OutputObservable}
import org.thp.thehive.models.{Observable, ObservableData, ObservableObservableType, ObservableTag, RichObservable}
import org.thp.thehive.services.{ObservableSrv, ObservableSteps}
import play.api.libs.json.{JsObject, Json}
import scala.collection.JavaConverters._
import scala.language.implicitConversions

import org.thp.scalligraph.controllers.Output

object ObservableConversion {
  import AlertConversion._
  import AttachmentConversion._
  import CaseConversion._

  implicit def fromInputObservable(inputObservable: InputObservable): Observable =
    inputObservable
      .into[Observable]
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.ioc, _.ioc.getOrElse(false))
      .withFieldComputed(_.sighted, _.sighted.getOrElse(false))
      .transform

  implicit def toOutputObservable(richObservable: RichObservable): Output[OutputObservable] =
    Output[OutputObservable](
      richObservable
        .into[OutputObservable]
        .withFieldConst(_._type, "case_artifact")
        .withFieldComputed(_.id, _.observable._id)
        .withFieldComputed(_._id, _.observable._id)
        .withFieldComputed(_.updatedAt, _.observable._updatedAt)
        .withFieldComputed(_.updatedBy, _.observable._updatedBy)
        .withFieldComputed(_.createdAt, _.observable._createdAt)
        .withFieldComputed(_.createdBy, _.observable._createdBy)
        .withFieldComputed(_.dataType, _.`type`.name)
        .withFieldComputed(_.startDate, _.observable._createdAt)
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldComputed(_.data, _.data.map(_.data))
        .withFieldComputed(_.attachment, _.attachment.map(toOutputAttachment(_).toOutput))
        .withFieldConst(_.stats, JsObject.empty)
        .transform
    )

  implicit def toOutputObservableWithStats(richObservableWithStats: (RichObservable, JsObject)): Output[OutputObservable] =
    Output[OutputObservable](
      richObservableWithStats
        ._1
        .into[OutputObservable]
        .withFieldConst(_._type, "case_artifact")
        .withFieldComputed(_.id, _.observable._id)
        .withFieldComputed(_._id, _.observable._id)
        .withFieldComputed(_.updatedAt, _.observable._updatedAt)
        .withFieldComputed(_.updatedBy, _.observable._updatedBy)
        .withFieldComputed(_.createdAt, _.observable._createdAt)
        .withFieldComputed(_.createdBy, _.observable._createdBy)
        .withFieldComputed(_.dataType, _.`type`.name)
        .withFieldComputed(_.startDate, _.observable._createdAt)
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldComputed(_.data, _.data.map(_.data))
        .withFieldComputed(_.attachment, _.attachment.map(toOutputAttachment(_).toOutput))
        .withFieldConst(_.stats, richObservableWithStats._2)
        .transform
    )

  def observableProperties(observableSrv: ObservableSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ObservableSteps]
      .property("status", UniMapping.string)(_.derived(_.constant("Ok")).readonly)
      .property("startDate", UniMapping.date)(_.derived(_.value(Key[Date]("_createdAt"))).readonly)
      .property("ioc", UniMapping.boolean)(_.simple.updatable)
      .property("sighted", UniMapping.boolean)(_.simple.updatable)
      .property("tags", UniMapping.string.set)(
        _.derived(_.outTo[ObservableTag].value("name"))
          .custom { (_, value, vertex, _, graph, authContext) =>
            observableSrv
              .getOrFail(vertex)(graph)
              .flatMap(observable => observableSrv.updateTags(observable, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("message", UniMapping.string)(_.simple.updatable)
      .property("tlp", UniMapping.int)(_.simple.updatable)
      .property("dataType", UniMapping.string)(_.derived(_.outTo[ObservableObservableType].value("name")).readonly)
      .property("data", UniMapping.string.optional)(_.derived(_.outTo[ObservableData].value("data")).readonly)
      // TODO add attachment ?
      .build

  def observableStatsRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    new ObservableSteps(_: GremlinScala[Vertex])
      .similar
      .visible
      .raw
      .groupCount(By(Key[Boolean]("ioc")))
      .map { stats =>
        val m      = stats.asScala
        val nTrue  = m.get(true).fold(0L)(_.toLong)
        val nFalse = m.get(false).fold(0L)(_.toLong)
        Json.obj(
          "seen" -> (nTrue + nFalse),
          "ioc"  -> (nTrue > 0)
        )
      }

  def observableLinkRenderer(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    (_: GremlinScala[Vertex])
      .coalesce(
        new ObservableSteps(_).alert.richAlert.map(a => Json.obj("alert" -> a.toJson)).raw,
        new ObservableSteps(_).`case`.richCase.map(c => Json.obj("case"  -> c.toJson)).raw
      )
}
