package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Log
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json._

import java.lang.{Long => JLong}
import java.util.{List => JList, Map => JMap}

trait LogRenderer extends BaseRenderer[Log] {

  private def caseParent(implicit
      authContext: AuthContext
  ): Traversal.V[Log] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.`case`.richCase.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  private def taskParent(implicit
      authContext: AuthContext
  ): Traversal.V[Log] => Traversal[JsValue, JMap[String, Any], Converter[JsValue, JMap[String, Any]]] =
    _.task.project(_.by(_.richTask.fold).by(_.`case`.richCase.fold)).domainMap {
      case (task, case0) =>
        task.headOption.fold[JsValue](JsNull)(_.toJson.as[JsObject] + ("case" -> case0.headOption.fold[JsValue](JsNull)(_.toJson)))
    }

  private def taskParentId: Traversal.V[Log] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.task.fold.domainMap(_.headOption.fold[JsValue](JsNull)(c => JsString(c._id.toString)))

  private def actionCount: Traversal.V[Log] => Traversal[JsValue, JLong, Converter[JsValue, JLong]] =
    _.in("ActionContext").count.domainMap(JsNumber(_))

  def logStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Log] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "case")        => addData("case", f)(caseParent)
        case (f, "task")        => addData("task", f)(taskParent)
        case (f, "taskId")      => addData("taskId", f)(taskParentId)
        case (f, "actionCount") => addData("actionCount", f)(actionCount)
        case (f, _)             => f
      }
    )
  }
}
