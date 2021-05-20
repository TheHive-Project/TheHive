package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.models.Taxonomy
import play.api.libs.json._

trait TaxonomyRenderer extends BaseRenderer[Taxonomy] {

  def enabledStats: Traversal.V[Taxonomy] => Traversal[JsValue, Boolean, Converter[JsValue, Boolean]] =
    _.enabled.domainMap(l => JsBoolean(l))

  def taxoStatsRenderer(extraData: Set[String]): Traversal.V[Taxonomy] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "enabled") => addData("enabled", f)(enabledStats)
        case (f, _)         => f
      }
    )
  }
}
