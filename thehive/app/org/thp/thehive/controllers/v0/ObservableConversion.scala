package org.thp.thehive.controllers.v0

import java.util.Date

import scala.collection.JavaConverters._
import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{By, Graph, GremlinScala, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputObservable, OutputObservable}
import org.thp.thehive.models.{Observable, ObservableData, RichObservable}
import org.thp.thehive.services.ObservableSteps

object ObservableConversion {
  import AttachmentConversion._
  import AlertConversion._
  import CaseConversion._

  implicit def fromInputObservable(inputObservable: InputObservable): Observable =
    inputObservable
      .into[Observable]
      .withFieldRenamed(_.dataType, _.`type`)
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
        .withFieldComputed(_.dataType, _.observable.`type`)
        .withFieldComputed(_.startDate, _.observable._createdAt)
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
        .withFieldComputed(_.dataType, _.observable.`type`)
        .withFieldComputed(_.startDate, _.observable._createdAt)
        .withFieldComputed(_.data, _.data.map(_.data))
        .withFieldComputed(_.attachment, _.attachment.map(toOutputAttachment(_).toOutput))
        .withFieldConst(_.stats, richObservableWithStats._2)
        .transform
    )

  val observableProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ObservableSteps]
      .property[String]("status")(_.derived(_.constant("Ok")).readonly)
      .property[Date]("startDate")(_.derived(_.value(Key[Date]("_createdAt"))).readonly)
      .property[Boolean]("ioc")(_.simple.updatable)
      .property[Boolean]("sighted")(_.simple.updatable)
      .property[Set[String]]("tags")(_.simple.updatable)
      .property[String]("message")(_.simple.updatable)
      .property[Int]("tlp")(_.simple.updatable)
      .property[String]("dataType")(_.rename("type").updatable)
      .property[Option[String]]("data")(_.derived(_.outTo[ObservableData].value(Key[String]("data"))).readonly)
      // TODO add attachment ?
      .build

  def observableStatsRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    new ObservableSteps(_: GremlinScala[Vertex])
      .similar
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
