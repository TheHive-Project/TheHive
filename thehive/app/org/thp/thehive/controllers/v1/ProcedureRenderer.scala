package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Procedure
import play.api.libs.json.{JsNull, JsValue}

import java.util.{List => JList, Map => JMap}

trait ProcedureRenderer extends BaseRenderer[Procedure] {
  def patternStats: Traversal.V[Procedure] => Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
    _.pattern.richPattern.domainMap(_.toJson)

  def patternParentStats: Traversal.V[Procedure] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.pattern.parent.richPattern.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  def procedureStatsRenderer(extraData: Set[String]): Traversal.V[Procedure] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "pattern")       => addData("pattern", f)(patternStats)
        case (f, "patternParent") => addData("patternParent", f)(patternParentStats)
        case (f, _)               => f
      }
    )
  }

}
