package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import gremlin.scala.{__, By, Graph, GremlinScala, Key, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.models.AlertCase
import org.thp.thehive.services.CaseSteps
import play.api.libs.json._

import scala.collection.JavaConverters._

trait CaseRenderer {

  def observableStats(
      caseSteps: CaseSteps
  )(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    caseSteps
      .share
      .observables
      .count
      .map(count => Json.obj("count" -> count))

  def taskStats(caseSteps: CaseSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    caseSteps
      .share
      .tasks
      .active
      .groupCount(By(Key[String]("status")))
      .map { statusAgg =>
        val (total, result) = statusAgg.asScala.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

  def alertStats(caseSteps: CaseSteps): Traversal[JsValue, JsValue] =
    caseSteps
      .inTo[AlertCase]
      .group(By(Key[String]("type")), By(Key[String]("source")))
      .map { alertAgg =>
        JsArray(
          alertAgg
            .asScala
            .flatMap {
              case (tpe, listOfSource) =>
                listOfSource.asScala.map(s => Json.obj("type" -> tpe, "source" -> s))
            }
            .toSeq
        )
      }

  def isOwnerStats(
      caseSteps: CaseSteps
  )(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    caseSteps.origin.name.map(org => JsBoolean(org == authContext.organisation))

  def shareCountStats(caseSteps: CaseSteps): Traversal[JsValue, JsValue] =
    caseSteps.organisations.count.map(c => JsNumber.apply(c - 1))

  def caseStatsRenderer(extraData: Set[String])(
      implicit authContext: AuthContext,
      db: Database,
      graph: Graph
  ): CaseSteps => Traversal[JsObject, JsObject] = {
    def addData(f: CaseSteps => Traversal[JsValue, JsValue]): GremlinScala[JMap[String, JsValue]] => GremlinScala[JMap[String, JsValue]] =
      _.by(f(new CaseSteps(__[Vertex])).raw.traversal)

    if (extraData.isEmpty) _.constant(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName
        .foldLeft[CaseSteps => GremlinScala[JMap[String, JsValue]]](_.raw.project(dataName.head, dataName.tail: _*)) {
          case (f, "observableStats") => f.andThen(addData(observableStats))
          case (f, "taskStats")       => f.andThen(addData(taskStats))
          case (f, "alerts")          => f.andThen(addData(alertStats))
          case (f, "isOwner")         => f.andThen(addData(isOwnerStats))
          case (f, "shareCount")      => f.andThen(addData(shareCountStats))
          case (f, _)                 => f.andThen(_.by(__.constant(JsNull).traversal))
        }
        .andThen(f => Traversal(f.map(m => JsObject(m.asScala))))
    }
  }
}
