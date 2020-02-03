package org.thp.thehive.controllers.v1

import scala.collection.JavaConverters._

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{__, By, Graph, Key, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.ObservableSteps

trait ObservableRenderer {

  def observableStatsRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): ObservableSteps => Traversal[JsObject, JsObject] =
    _.project(
      _.apply(By(new ObservableSteps(__[Vertex]).shares.organisation.name.fold.raw))
        .and(
          By(
            new ObservableSteps(__[Vertex])
              .similar
              .visible
              .groupCount(By(Key[Boolean]("ioc")))
              .raw
          )
        )
    ).map {
      case (organisationNames, stats) =>
        val m      = stats.asScala
        val nTrue  = m.get(true).fold(0L)(_.toLong)
        val nFalse = m.get(false).fold(0L)(_.toLong)
        Json.obj(
          "shares" -> organisationNames.asScala,
          "seen"   -> (nTrue + nFalse),
          "ioc"    -> (nTrue > 0)
        )
    }

  def observableLinkRenderer: ObservableSteps => Traversal[JsObject, JsObject] =
    _.coalesce(
      _.alert.richAlert.map(a => Json.obj("alert"            -> a.toJson)),
      _.`case`.richCaseWithoutPerms.map(c => Json.obj("case" -> c.toJson))
    )
}
