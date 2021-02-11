package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Procedure
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.ProcedureOps._
import play.api.libs.json.JsValue

import java.util.{Map => JMap}

trait ProcedureRenderer extends BaseRenderer[Procedure] {
  def patternStats: Traversal.V[Procedure] => Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
    _.pattern.richPattern.domainMap(_.toJson)

  def procedureStatsRenderer(extraData: Set[String]): Traversal.V[Procedure] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "pattern") => addData("pattern", f)(patternStats)
        case (f, _)         => f
      }
    )
  }

}
