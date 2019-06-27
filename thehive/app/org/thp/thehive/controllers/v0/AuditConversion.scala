package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{__, BranchCase, BranchOtherwise, By, Graph, GremlinScala, Label, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{OutputAudit, OutputEntity}
import org.thp.thehive.models.{Audited, RichAudit, ShareCase, ShareTask, TaskLog}
import org.thp.thehive.services.{CaseSteps, LogSteps, TaskSteps}

object AuditConversion {
  import CaseConversion._
  import TaskConversion._
  import LogConversion._

  def actionToOperation(action: String): String = action match {
    case "createCase"         ⇒ "Creation"
    case "updateCase"         ⇒ "Update"
    case "createAlert"        ⇒ "Creation"
    case "updateAlert"        ⇒ "Update"
    case "createCaseTemplate" ⇒ "Creation"
    case "createLog"          ⇒ "Creation"
    case "updateLog"          ⇒ "Update"
    case "createTask"         ⇒ "Creation"
    case _                    ⇒ "Unknown"
  }

  def objectTypeMapper(objectType: String): String = objectType match {
//    case "Case" =>"case"
    case "Task" ⇒ "case_task"
    case "Log"  ⇒ "case_task_log"
    case other  ⇒ other.toLowerCase()
  }

  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    Output[OutputAudit](
      audit
        .into[OutputAudit]
        .withFieldComputed(_.operation, a ⇒ actionToOperation(a.action)) // TODO map action to operation
        .withFieldComputed(_.id, _._id)
        .withFieldComputed(_.createdAt, _._createdAt)
        .withFieldComputed(_.createdBy, _._createdBy)
        .withFieldComputed(_.`object`, _.`object`.map(OutputEntity.apply)) //objectToJson))
        .withFieldConst(_.base, true)
        .withFieldComputed(_.details, a ⇒ Json.parse(a.details.getOrElse("{}")).as[JsObject])
        .withFieldComputed(_.objectId, a ⇒ a.`object`.getOrElse(a.context)._id)
        .withFieldComputed(_.objectType, a ⇒ objectTypeMapper(a.`object`.getOrElse(a.context)._model.label))
        .withFieldComputed(_.rootId, _._id)
        .withFieldComputed(_.startDate, _._createdAt)
        .withFieldComputed(_.summary, a ⇒ Map(objectTypeMapper(a.`object`.getOrElse(a.context)._model.label) → Map(a.action → 1)))
        .transform
    )

  def caseToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[(String, JsObject)] =
    new CaseSteps(_).richCase.map[(String, JsObject)](c ⇒ c._id → c.toJson.as[JsObject]).raw

  def taskToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[(String, JsObject)] =
    _.project(
      _.apply(By(new TaskSteps(__[Vertex]).richTask.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(caseToJson(db, graph)(__[Vertex].inTo[ShareTask].outTo[ShareCase])))
    ).map {
      case (task, (rootId, case0)) ⇒ rootId → (task + ("case" → case0))
    }

  def logToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[(String, JsObject)] =
    _.project(
      _.apply(By(new LogSteps(__[Vertex]).richLog.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(taskToJson(db, graph)(__[Vertex].inTo[TaskLog])))
    ).map {
      case (log, (rootId, task)) ⇒ rootId → (log + ("case_task" → task))
    }

  def auditRenderer(implicit db: Database, graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[(String, JsObject)] =
    (_: GremlinScala[Vertex])
      .outTo[Audited]
      .choose[Label, (String, JsObject)](
        on = _.label(),
        BranchCase("Case", caseToJson),
        BranchCase("Task", taskToJson),
        BranchCase("Log", logToJson),
        BranchOtherwise(_.constant("" → JsObject.empty))
      )
}
