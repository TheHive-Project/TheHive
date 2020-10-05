package org.thp.thehive.controllers.v1

import java.util.{List => JList, Map => JMap}

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.{Alert, RichCase, SimilarStats}
import org.thp.thehive.services.AlertOps._
import play.api.libs.json._

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
  def similarCasesStats(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] = {
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
    _.similarCases.fold.domainMap(sc => JsArray(sc.sorted.map(Json.toJson(_))))
  }

  def alertStatsRenderer[D, G, C <: Converter[D, G]](extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { traversal =>
    def addData[T](
        name: String
    )(f: Traversal.V[Alert] => Traversal[JsValue, T, Converter[JsValue, T]]): Traversal[JsObject, JMap[String, Any], Converter[
      JsObject,
      JMap[String, Any]
    ]] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { t =>
      val dataTraversal = f(traversal.start)
      t.onRawMap[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]](_.by(dataTraversal.raw)) { jmap =>
        t.converter(jmap) + (name -> dataTraversal.converter(jmap.get(name).asInstanceOf[T]))
      }
    }

    if (extraData.isEmpty) traversal.constant2(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName.foldLeft[Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]]](
        traversal.onRawMap[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]](_.project(dataName.head, dataName.tail: _*))(_ =>
          JsObject.empty
        )
      ) {
        case (f, "similarCases") => addData("similarCases")(similarCasesStats)(f)
        case (f, _)              => f
      }
    }
  }
}
