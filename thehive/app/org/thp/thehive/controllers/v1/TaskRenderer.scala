package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Task
import play.api.libs.json._

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.{List => JList, Map => JMap}

trait TaskRenderer extends BaseRenderer[Task] {

  private def caseParent(implicit
      authContext: AuthContext
  ): Traversal.V[Task] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.`case`.richCase.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  private def caseParentId: Traversal.V[Task] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.`case`.fold.domainMap(_.headOption.fold[JsValue](JsNull)(c => JsString(c._id.toString)))

  private def caseTemplateParent: Traversal.V[Task] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.caseTemplate.richCaseTemplate.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  private def caseTemplateParentId: Traversal.V[Task] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.caseTemplate.fold.domainMap(_.headOption.fold[JsValue](JsNull)(ct => JsString(ct._id.toString)))

  private def shareCount: Traversal.V[Task] => Traversal[JsValue, JLong, Converter[JsValue, JLong]] =
    _.organisations.count.domainMap(count => JsNumber(count - 1))

  private def isOwner(implicit authContext: AuthContext): Traversal.V[Task] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.origin.get(authContext.organisation).fold.domainMap(l => JsBoolean(l.nonEmpty))

  private def actionRequired(implicit authContext: AuthContext): Traversal.V[Task] => Traversal[JsValue, JBoolean, Converter[JsValue, JBoolean]] =
    _.actionRequired.domainMap(JsBoolean(_))

  private def actionRequiredMap(implicit
      authContext: AuthContext
  ): Traversal.V[Task] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.actionRequiredMap.fold.domainMap(_.toMap.toJson)

  def taskStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Task] => JsTraversal = { implicit traversal =>
    baseRenderer(
      extraData,
      traversal,
      {
        case (f, "case")              => addData("case", f)(caseParent)
        case (f, "caseId")            => addData("caseId", f)(caseParentId)
        case (f, "caseTemplate")      => addData("caseTemplate", f)(caseTemplateParent)
        case (f, "caseTemplateId")    => addData("caseTemplateId", f)(caseTemplateParentId)
        case (f, "isOwner")           => addData("isOwner", f)(isOwner)
        case (f, "shareCount")        => addData("shareCount", f)(shareCount)
        case (f, "actionRequired")    => addData("actionRequired", f)(actionRequired)
        case (f, "actionRequiredMap") => addData("actionRequiredMap", f)(actionRequiredMap)
        case (f, _)                   => f
      }
    )
  }
}
