package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import gremlin.scala.{__, Graph, GremlinScala, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{RichCase, SimilarStats}
import org.thp.thehive.services.AlertSteps
import play.api.libs.json._

import scala.collection.JavaConverters._
trait AlertRenderer {
  implicit val similarCaseWrites: Writes[(RichCase, SimilarStats)] = Writes[(RichCase, SimilarStats)] {
    case (richCase, similarStats) =>
      Json.obj(
        "case"                   -> richCase.toJson,
        "similarObservableCount" -> similarStats.observable._1,
        "observableCount"        -> similarStats.observable._2,
        "similarIocCount"        -> similarStats.ioc._1,
        "iocCount"               -> similarStats.ioc._2
      )
  }
  def similarCasesStats(alertSteps: AlertSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] = {
    implicit val similarCaseOrdering: Ordering[(RichCase, SimilarStats)] = (x: (RichCase, SimilarStats), y: (RichCase, SimilarStats)) =>
      //negative if x < y
      if (x._1._createdAt after y._1._createdAt) -1
      else if (x._1._createdAt before y._1._createdAt) 1
      else if (x._2.observable._1 > y._2.observable._1) -1
      else if (x._2.observable._1 < y._2.observable._1) 1
      else if (x._2.ioc._1 > y._2.ioc._1) -1
      else if (x._2.ioc._1 < y._2.ioc._1) 1
      else if (x._2.ioc._2 > y._2.ioc._2) -1
      else if (x._2.ioc._2 < y._2.ioc._2) 1
      else 0
    alertSteps.similarCases.fold.map(sc => JsArray(sc.asScala.sorted.map(Json.toJson(_))))
  }

  def alertStatsRenderer(extraData: Set[String])(
      implicit authContext: AuthContext,
      db: Database,
      graph: Graph
  ): AlertSteps => Traversal[JsObject, JsObject] = {
    def addData(f: AlertSteps => Traversal[JsValue, JsValue]): GremlinScala[JMap[String, JsValue]] => GremlinScala[JMap[String, JsValue]] =
      _.by(f(new AlertSteps(__[Vertex])).raw.traversal)

    if (extraData.isEmpty) _.constant(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName
        .foldLeft[AlertSteps => GremlinScala[JMap[String, JsValue]]](_.raw.project(dataName.head, dataName.tail: _*)) {
          case (f, "similarCases") => f.andThen(addData(similarCasesStats))
          case (f, _)              => f.andThen(_.by(__.constant(JsNull).traversal))
        }
        .andThen(f => Traversal(f.map(m => JsObject(m.asScala))))
    }
  }
}
