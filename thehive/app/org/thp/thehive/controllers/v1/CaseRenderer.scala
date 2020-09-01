package org.thp.thehive.controllers.v1

import java.lang.{Long => JLong}
import java.util.{Collection => JCollection, List => JList, Map => JMap}

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.{Alert, AlertCase, Case}
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ShareOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json._

trait CaseRenderer {

  def observableStats(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsObject, JLong, Converter[JsObject, JLong]] =
    _.share
      .observables
      .count
      .domainMap(count => Json.obj("total" -> count))

  def taskStats(implicit
      authContext: AuthContext
  ): Traversal.V[Case] => Traversal[JsObject, JMap[String, JLong], Converter[JsObject, JMap[String, JLong]]] =
    _.share
      .tasks
      .active
      .groupCount(_.byValue(_.status))
      .domainMap { statusAgg =>
        val (total, result) = statusAgg.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k.toString -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

  def alertStats: Traversal.V[Case] => Traversal[JsArray, JMap[String, JCollection[String]], Converter[JsArray, JMap[String, JCollection[String]]]] =
    _.in[AlertCase]
      .v[Alert]
      .group(_.byValue(_.`type`), _.byValue(_.source))
      .domainMap { alertAgg =>
        JsArray((for {
          (tpe, sources) <- alertAgg
          source         <- sources
        } yield Json.obj("type" -> tpe, "source" -> source)).toSeq)
      }

  def isOwnerStats(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsBoolean, JList[Vertex], Converter[JsBoolean, JList[Vertex]]] =
    _.origin.has("name", authContext.organisation).fold.domainMap(l => JsBoolean(l.nonEmpty))

  def shareCountStats: Traversal.V[Case] => Traversal[JsNumber, JLong, Converter[JsNumber, JLong]] =
    _.organisations.count.domainMap(c => JsNumber(c - 1))

  def permissions(implicit authContext: AuthContext): Traversal.V[Case] => Traversal[JsValue, Vertex, Converter[JsValue, Vertex]] =
    _.userPermissions.domainMap(permissions => Json.toJson(permissions))

  def caseStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Case] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { traversal =>
    def addData[G](
        name: String
    )(f: Traversal.V[Case] => Traversal[JsValue, G, Converter[JsValue, G]]): Traversal[JsObject, JMap[String, Any], Converter[
      JsObject,
      JMap[String, Any]
    ]] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { t =>
      val dataTraversal = f(traversal.start)
      t.onRawMap[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]](_.by(dataTraversal.raw)) { jmap =>
        t.converter(jmap) + (name -> dataTraversal.converter(jmap.get(name).asInstanceOf[G]))
      }
    }

    if (extraData.isEmpty) traversal.constant2[JsObject, JMap[String, Any]](JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName.foldLeft[Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]]](
        traversal.onRawMap[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]](_.project(dataName.head, dataName.tail: _*))(_ =>
          JsObject.empty
        )
      ) {
        case (f, "observableStats") => addData("observableStats")(observableStats)(f)
        case (f, "taskStats")       => addData("taskStats")(taskStats)(f)
        case (f, "alerts")          => addData("alerts")(alertStats)(f)
        case (f, "isOwner")         => addData("isOwner")(isOwnerStats)(f)
        case (f, "shareCount")      => addData("shareCount")(shareCountStats)(f)
        case (f, "permissions")     => addData("permissions")(permissions)(f)
        case (f, _)                 => f
      }
    }
  }
}
