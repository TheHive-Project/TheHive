package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import gremlin.scala.{__, Graph, GremlinScala, Vertex}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.services.LogSteps
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue}

import scala.collection.JavaConverters._

trait LogRenderer {

  def taskParent(logSteps: LogSteps): Traversal[JsValue, JsValue] =
    logSteps.task.richTask.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(_.toJson))

  def taskParentId(logSteps: LogSteps): Traversal[JsValue, JsValue] =
    logSteps.task.fold.map(_.asScala.headOption.fold[JsValue](JsNull)(c => JsString(c.id().toString)))

  def logStatsRenderer(extraData: Set[String])(implicit db: Database, graph: Graph): LogSteps => Traversal[JsObject, JsObject] = {
    def addData(f: LogSteps => Traversal[JsValue, JsValue]): GremlinScala[JMap[String, JsValue]] => GremlinScala[JMap[String, JsValue]] =
      _.by(f(new LogSteps(__[Vertex])).raw.traversal)

    if (extraData.isEmpty) _.constant(JsObject.empty)
    else {
      val dataName = extraData.toSeq
      dataName
        .foldLeft[LogSteps => GremlinScala[JMap[String, JsValue]]](_.raw.project(dataName.head, dataName.tail: _*)) {
          case (f, "task")   => f.andThen(addData(taskParent))
          case (f, "taskId") => f.andThen(addData(taskParentId))
          case (f, _)        => f.andThen(_.by(__.constant(JsNull).traversal))
        }
        .andThen(f => Traversal(f.map(m => JsObject(m.asScala))))
    }
  }

}
