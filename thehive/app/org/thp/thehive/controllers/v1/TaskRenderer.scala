package org.thp.thehive.controllers.v1

import java.lang.{Long => JLong}
import java.util.{List => JList, Map => JMap}

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models.Task
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.TaskOps._
import play.api.libs.json._

trait TaskRenderer {

  def caseParent(implicit
      authContext: AuthContext
  ): Traversal.V[Task] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.`case`.richCase.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  def caseParentId: Traversal.V[Task] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.`case`.fold.domainMap(_.headOption.fold[JsValue](JsNull)(c => JsString(c._id)))

  def caseTemplateParent: Traversal.V[Task] => Traversal[JsValue, JList[JMap[String, Any]], Converter[JsValue, JList[JMap[String, Any]]]] =
    _.caseTemplate.richCaseTemplate.fold.domainMap(_.headOption.fold[JsValue](JsNull)(_.toJson))

  def caseTemplateParentId: Traversal.V[Task] => Traversal[JsValue, JList[Vertex], Converter[JsValue, JList[Vertex]]] =
    _.caseTemplate.fold.domainMap(_.headOption.fold[JsValue](JsNull)(ct => JsString(ct._id)))

  def shareCount: Traversal.V[Task] => Traversal[JsValue, JLong, Converter[JsValue, JLong]] =
    _.organisations.count.domainMap(count => JsNumber(count - 1))

  def isOwner(implicit authContext: AuthContext): Traversal.V[Task] => Traversal[JsBoolean, JList[Vertex], Converter[JsBoolean, JList[Vertex]]] =
    _.origin.has("name", authContext.organisation).fold.domainMap(l => JsBoolean(l.nonEmpty))

  def taskStatsRenderer(extraData: Set[String])(implicit
      authContext: AuthContext
  ): Traversal.V[Task] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] = { traversal =>
    def addData[G](
        name: String
    )(f: Traversal.V[Task] => Traversal[JsValue, G, Converter[JsValue, G]]): Traversal[JsObject, JMap[String, Any], Converter[
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
        case (f, "case")           => addData("case")(caseParent)(f)
        case (f, "caseId")         => addData("caseId")(caseParentId)(f)
        case (f, "caseTemplate")   => addData("caseTemplate")(caseTemplateParent)(f)
        case (f, "caseTemplateId") => addData("caseTemplateId")(caseTemplateParentId)(f)
        case (f, "isOwner")        => addData("isOwner")(isOwner)(f)
        case (f, "shareCount")     => addData("shareCount")(shareCount)(f)
        case (f, _)                => f
      }
    }
  }
}
