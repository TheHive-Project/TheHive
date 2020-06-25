package org.thp.thehive.controllers.v0

import gremlin.scala.{By, Key}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.ObservableSteps
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._

trait ObservableRenderer {

  def observableStatsRenderer(implicit authContext: AuthContext): ObservableSteps => Traversal[JsObject, JsObject] =
    _.similar
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

  def observableLinkRenderer: ObservableSteps => Traversal[JsObject, JsObject] =
    _.coalesce(
      _.alert.richAlert.map(a => Json.obj("alert"            -> a.toJson)),
      _.`case`.richCaseWithoutPerms.map(c => Json.obj("case" -> c.toJson))
    )
}
