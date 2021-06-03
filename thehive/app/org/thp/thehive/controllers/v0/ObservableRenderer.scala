package org.thp.thehive.controllers.v0

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.Traversal.V
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.Observable
import org.thp.thehive.services.TheHiveOps
import play.api.libs.json.{JsObject, Json}

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.{Map => JMap}

trait ObservableRenderer extends TheHiveOps {

  def observableStatsRenderer(implicit
      authContext: AuthContext
  ): Traversal.V[Observable] => Traversal[JsObject, JMap[JBoolean, JLong], Converter[JsObject, JMap[JBoolean, JLong]]] =
    _.filteredSimilar
      .visible
      .groupCount(_.byValue(_.ioc))
      .domainMap { stats =>
        val nTrue  = stats.getOrElse(true, 0L)
        val nFalse = stats.getOrElse(false, 0L)
        Json.obj(
          "seen" -> (nTrue + nFalse),
          "ioc"  -> (nTrue > 0)
        )
      }

  def observableLinkRenderer: V[Observable] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.coalesceMulti(
      _.alert.richAlert.domainMap(a => Json.obj("alert" -> a.toJson)),
      _.`case`.richCaseWithoutPerms.domainMap(c => Json.obj("case" -> c.toJson)),
      _.constant2[JsObject, JMap[String, Any]](JsObject.empty)
    )
}
