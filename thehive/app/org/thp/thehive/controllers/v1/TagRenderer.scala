package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.Tag
import org.thp.thehive.services.TagOps._
import play.api.libs.json._

import java.util.{Map => JMap}

trait TagRenderer extends BaseRenderer[Tag] {

  def usageStats(implicit
      authContext: AuthContext
  ): Traversal.V[Tag] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(_.`case`.count)
        .by(_.alert.count)
        .by(_.observable.count)
    ).domainMap {
      case (caseCount, alertCount, observableCount) =>
        Json.obj(
          "case"       -> caseCount,
          "alert"      -> alertCount,
          "observable" -> observableCount
        )
    }

  def tagStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Tag] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "usage") => addData("usage", f)(usageStats)
        case (f, _)       => f
      }
    )
  }
}
