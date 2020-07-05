package org.thp.thehive.controllers.v0

import java.lang.{Long => JLong}
import java.util.{Map => JMap}

import gremlin.scala.{__, By, Graph, Key, Vertex}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services._
import play.api.libs.json.{JsNumber, JsObject}

import scala.collection.JavaConverters._

trait AuditRenderer {

  def caseToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asCase.richCaseWithoutPerms.map[JsObject](_.toJson.as[JsObject])

  def taskToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] = entitySteps => {
    val taskSteps = entitySteps.asTask
    taskSteps
      .project(
        _.by(_.richTask.map(_.toJson))
          .by(t => caseToJson(t.`case`))
      )
      .map {
        case (task, case0) => task.as[JsObject] + ("case" -> case0)
      }
  }

  def alertToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asAlert.richAlert.map(_.toJson.as[JsObject])

  def logToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asLog
      .project(
        _.by(_.richLog.map(_.toJson))
          .by(l => taskToJson(l.task))
      )
      .map { case (log, task) => log.as[JsObject] + ("case_task" -> task) }

  def observableToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asObservable
      .project(
        _.by(_.richObservable.map(_.toJson))
          .by(_.coalesce(o => caseToJson(o.`case`), o => alertToJson(o.alert)))
      )
      .map {
        case (obs, caseOrAlert) => obs.as[JsObject] + ((caseOrAlert \ "_type").asOpt[String].getOrElse("???") -> caseOrAlert)
      }

  def auditRenderer: AuditSteps => Traversal[JsObject, JsObject] =
    (_: AuditSteps)
      .coalesce[JsObject](
        _.`object` //.outTo[Audited]
          .choose(
            on = _.label,
            BranchCase("Case", caseToJson),
            BranchCase("Task", taskToJson),
            BranchCase("Log", logToJson),
            BranchCase("Observable", observableToJson),
            BranchCase("Alert", alertToJson),
            BranchOtherwise(_.constant(JsObject.empty))
          ),
        _.constant(JsObject.empty)
      )

  def jsonSummary(auditSrv: AuditSrv, requestId: String)(implicit graph: Graph): JsObject =
    auditSrv
      .initSteps
      .has("requestId", requestId)
      .has("mainAction", false)
      .groupBy(By(Key[String]("objectType")), By(__[Vertex].groupCount(By(Key[String]("action")))))
      .headOption()
      .fold(JsObject.empty) { m: JMap[String, java.util.Collection[JMap[String, JLong]]] =>
        JsObject(
          m.asInstanceOf[JMap[String, JMap[String, JLong]]]
            .asScala
            .map {
              case (o, ac) =>
                fromObjectType(o) -> JsObject(ac.asScala.map { case (a, c) => actionToOperation(a) -> JsNumber(c.toLong) }.toSeq)
            }
        )
      }

}
