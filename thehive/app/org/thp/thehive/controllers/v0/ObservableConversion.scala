package org.thp.thehive.controllers.v0

import scala.collection.JavaConverters._

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{By, Graph, GremlinScala, Key, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.ObservableSteps

object ObservableConversion {

  def observableStatsRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    new ObservableSteps(_: GremlinScala[Vertex])
      .similar
      .visible
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
      .raw

  def observableLinkRenderer(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    (_: GremlinScala[Vertex])
      .coalesce(
        new ObservableSteps(_).alert.richAlert.map(a => Json.obj("alert"            -> a.toJson)).raw,
        new ObservableSteps(_).`case`.richCaseWithoutPerms.map(c => Json.obj("case" -> c.toJson)).raw
      )
}
