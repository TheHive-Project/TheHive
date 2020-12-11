package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.Taxonomy
import org.thp.thehive.services.TaxonomyOps._
import play.api.libs.json._

import java.util.{Map => JMap}

trait TaxonomyRenderer {

  def enabledStats: Traversal.V[Taxonomy] => Traversal[JsValue, Boolean, Converter[JsValue, Boolean]] =
    _.enabled.domainMap(l => JsBoolean(l))

  def taxoStatsRenderer(extraData: Set[String]):
    Traversal.V[Taxonomy] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { traversal =>
    def addData[G](
        name: String
    )(f: Traversal.V[Taxonomy] => Traversal[JsValue, G, Converter[JsValue, G]]): Traversal[JsObject, JMap[String, Any], Converter[
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
        case (f, "enabled") => addData("enabled")(enabledStats)(f)
        case (f, _)         => f
      }
    }
  }
}
