package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import gremlin.scala.{__, By, Graph, GremlinScala, Key, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.ObservableSteps
import play.api.libs.json._

import scala.collection.JavaConverters._

trait ObservableRenderer {

  def seen(observableSteps: ObservableSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    observableSteps
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

  def shares(observableSteps: ObservableSteps): Traversal[JsValue, JsValue] =
    observableSteps.shares.organisation.name.fold.map(orgs => Json.toJson(orgs.asScala))

  def shareCount(observableSteps: ObservableSteps): Traversal[JsValue, JsValue] =
    observableSteps.organisations.count.map(c => JsNumber(c - 1))

  def isOwner(
      observableSteps: ObservableSteps
  )(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    observableSteps.origin.has("name", authContext.organisation).fold.map(l => JsBoolean(!l.isEmpty))

  def observableLinks(observableSteps: ObservableSteps): Traversal[JsValue, JsValue] =
    observableSteps.coalesce(
      _.alert.richAlert.map(a => Json.obj("alert"            -> a.toJson)),
      _.`case`.richCaseWithoutPerms.map(c => Json.obj("case" -> c.toJson))
    )

  def permissions(observableSteps: ObservableSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    observableSteps.userPermissions.map(permissions => Json.toJson(permissions))

  def observableStatsRenderer(extraData: Set[String])(
      implicit authContext: AuthContext,
      db: Database,
      graph: Graph
  ): ObservableSteps => Traversal[JsObject, JsObject] = {
    def addData(f: ObservableSteps => Traversal[JsValue, JsValue]): GremlinScala[JMap[String, JsValue]] => GremlinScala[JMap[String, JsValue]] =
      _.by(f(new ObservableSteps(__[Vertex])).raw.traversal)

    if (extraData.isEmpty) _.constant(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName
        .foldLeft[ObservableSteps => GremlinScala[JMap[String, JsValue]]](_.raw.project(dataName.head, dataName.tail: _*)) {
          case (f, "seen")        => f.andThen(addData(seen))
          case (f, "shares")      => f.andThen(addData(shares))
          case (f, "isOwner")     => f.andThen(addData(isOwner))
          case (f, "shareCount")  => f.andThen(addData(shareCount))
          case (f, "links")       => f.andThen(addData(observableLinks))
          case (f, "permissions") => f.andThen(addData(permissions))
          case (f, _)             => f.andThen(_.by(__.constant(JsNull).traversal))
        }
        .andThen(f => Traversal(f.map(m => JsObject(m.asScala))))
    }
  }
}
