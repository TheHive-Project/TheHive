package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import gremlin.scala.{__, Graph, GremlinScala, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.services.TaskSteps
import play.api.libs.json._

import scala.collection.JavaConverters._

trait TaskRenderer {

  def caseParent(taskSteps: TaskSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    taskSteps.`case`.richCase.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(_.toJson))

  def caseParentId(taskSteps: TaskSteps): Traversal[JsValue, JsValue] =
    taskSteps.`case`.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(c => JsString(c.id().toString)))

  def caseTemplateParent(taskSteps: TaskSteps): Traversal[JsValue, JsValue] =
    taskSteps.caseTemplate.richCaseTemplate.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(_.toJson))

  def caseTemplateParentId(taskSteps: TaskSteps): Traversal[JsValue, JsValue] =
    taskSteps.caseTemplate.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(ct => JsString(ct.id().toString)))

  def shareCount(taskSteps: TaskSteps): Traversal[JsValue, JsValue] =
    taskSteps.organisations.count.map(c => JsNumber(c - 1))

  def isOwner(taskSteps: TaskSteps)(implicit authContext: AuthContext): Traversal[JsValue, JsValue] =
    taskSteps.origin.has("name", authContext.organisation).fold.map(l => JsBoolean(!l.isEmpty))

  def taskStatsRenderer(extraData: Set[String])(
      implicit authContext: AuthContext,
      db: Database,
      graph: Graph
  ): TaskSteps => Traversal[JsObject, JsObject] = {
    def addData(f: TaskSteps => Traversal[JsValue, JsValue]): GremlinScala[JMap[String, JsValue]] => GremlinScala[JMap[String, JsValue]] =
      _.by(f(new TaskSteps(__[Vertex])).raw.traversal)

    if (extraData.isEmpty) _.constant(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName
        .foldLeft[TaskSteps => GremlinScala[JMap[String, JsValue]]](_.raw.project(dataName.head, dataName.tail: _*)) {
          case (f, "case")           => f.andThen(addData(caseParent))
          case (f, "caseId")         => f.andThen(addData(caseParentId))
          case (f, "caseTemplate")   => f.andThen(addData(caseTemplateParent))
          case (f, "caseTemplateId") => f.andThen(addData(caseTemplateParentId))
          case (f, "isOwner")        => f.andThen(addData(isOwner))
          case (f, "shareCount")     => f.andThen(addData(shareCount))
          case (f, _)                => f.andThen(_.by(__.constant(JsNull).traversal))
        }
        .andThen(f => Traversal(f.map(m => JsObject(m.asScala))))
    }
  }
}
