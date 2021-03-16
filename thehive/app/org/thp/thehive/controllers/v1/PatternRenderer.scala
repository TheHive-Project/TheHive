package org.thp.thehive.controllers.v1

import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Pattern
import org.thp.thehive.services.PatternOps._
import play.api.libs.json._

import java.util.{List => JList, Map => JMap}

trait PatternRenderer extends BaseRenderer[Pattern] {

  private def parent: Traversal.V[Pattern] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.parent.richPattern.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  private def children: Traversal.V[Pattern] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.children.richPattern.fold.domainMap(_.toJson)

  def patternRenderer(extraData: Set[String]): Traversal.V[Pattern] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "children") => addData("children", f)(children)
        case (f, "parent")   => addData("parent", f)(parent)
        case (f, _)          => f
      }
    )
  }
}
