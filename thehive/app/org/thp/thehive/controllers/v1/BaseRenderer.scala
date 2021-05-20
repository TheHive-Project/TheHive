package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.services.TheHiveOpsNoDeps
import play.api.libs.json.{JsObject, JsValue}

import java.util.{Map => JMap}

trait BaseRenderer[A] extends TheHiveOpsNoDeps {

  type JsConverter = Converter[JsObject, JMap[String, Any]]
  type JsTraversal = Traversal[JsObject, JMap[String, Any], JsConverter]
  def baseRenderer(
      extraData: Set[String],
      traversal: Traversal.V[A],
      mapping: (JsTraversal, String) => JsTraversal
  ): JsTraversal =
    if (extraData.isEmpty) traversal.constant2[JsObject, JMap[String, Any]](JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName.foldLeft[JsTraversal](
        traversal.onRawMap[JsObject, JMap[String, Any], JsConverter](_.project(dataName.head, dataName.tail: _*))(_ => JsObject.empty)
      )(mapping)
    }

  def addData[G](name: String, jsTraversal: JsTraversal)(
      f: Traversal.V[A] => Traversal[JsValue, G, Converter[JsValue, G]]
  )(implicit traversal: Traversal.V[A]): JsTraversal = {
    val dataTraversal = f(traversal.start)
    jsTraversal.onRawMap[JsObject, JMap[String, Any], JsConverter](_.by(dataTraversal.raw)) { jmap =>
      jsTraversal.converter(jmap) + (name -> dataTraversal.converter(jmap.get(name).asInstanceOf[G]))
    }
  }

}
