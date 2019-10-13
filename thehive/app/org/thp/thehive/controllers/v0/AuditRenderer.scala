package org.thp.thehive.controllers.v0

import gremlin.scala.By
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services._
import play.api.libs.json.JsObject

trait AuditRenderer {

  def caseToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asCase.richCaseWithoutPerms.map[JsObject](_.toJson.as[JsObject])

  def taskToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] = entitySteps => {
    val taskSteps = entitySteps.asTask
    taskSteps
      .project(
        _.apply(By(taskSteps.start().richTask.map[JsObject](_.toJson.as[JsObject]).raw))
          .and(By(caseToJson(taskSteps.start().`case`).raw))
      )
      .map {
        case (task, case0) => task + ("case" -> case0)
      }
  }

  def alertToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] =
    _.asAlert.richAlert.map(_.toJson.as[JsObject])

  def logToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] = entitySteps => {
    val logSteps = entitySteps.asLog
    logSteps
      .project(
        _.apply(By(logSteps.start().richLog.map[JsObject](_.toJson.as[JsObject]).raw))
          .and(By(taskToJson(logSteps.start().task).raw))
      )
      .map {
        case (log, task) => log + ("case_task" -> task)
      }
  }

  def observableToJson: VertexSteps[_ <: Product] => Traversal[JsObject, JsObject] = entitySteps => {
    val observableSteps = entitySteps.asObservable
    observableSteps
      .project(
        _.apply(By(observableSteps.start().richObservable.map[JsObject](_.toJson.as[JsObject]).raw))
          .and(By(observableSteps.start().coalesce(o => caseToJson(o.`case`), o => alertToJson(o.alert)).raw))
      )
      .map {
        case (obs, c) => obs + ((c \ "_type").asOpt[String].getOrElse("???") -> c)
      }
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

}
