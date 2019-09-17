package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import play.api.libs.json.{JsObject, Json}

import gremlin.scala.{__, BranchCase, BranchOtherwise, By, Graph, GremlinScala, Label, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Database, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{OutputAudit, OutputEntity}
import org.thp.thehive.models._
import org.thp.thehive.services.{AuditSteps, CaseSteps, LogSteps, TaskSteps}

object AuditConversion {
  import CaseConversion._
  import LogConversion._
  import TaskConversion._

  def actionToOperation(action: String): String = action match {
    case "create" => "Creation"
    case "update" => "Update"
    case _        => "Unknown"
  }

  def objectTypeMapper(objectType: String): String = objectType match {
//    case "Case" =>"case"
    case "Task" => "case_task"
    case "Log"  => "case_task_log"
    case other  => other.toLowerCase()
  }

  implicit def toOutputAudit(audit: RichAudit): Output[OutputAudit] =
    Output[OutputAudit](
      audit
        .into[OutputAudit]
        .withFieldComputed(_.operation, a => actionToOperation(a.action)) // TODO map action to operation
        .withFieldComputed(_.id, _._id)
        .withFieldComputed(_.createdAt, _._createdAt)
        .withFieldComputed(_.createdBy, _._createdBy)
        .withFieldComputed(_.`object`, _.`object`.map(OutputEntity.apply)) //objectToJson))
        .withFieldConst(_.base, true)
        .withFieldComputed(_.details, a => Json.parse(a.details.getOrElse("{}")).as[JsObject])
        .withFieldComputed(_.objectId, a => a.`object`.getOrElse(a.context)._id)
        .withFieldComputed(_.objectType, a => objectTypeMapper(a.`object`.getOrElse(a.context)._model.label))
        .withFieldComputed(_.rootId, _._id)
        .withFieldComputed(_.startDate, _._createdAt)
        .withFieldComputed(_.summary, a => Map(objectTypeMapper(a.`object`.getOrElse(a.context)._model.label) -> Map(a.action -> 1)))
        .transform
    )

  def caseToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[(String, JsObject)] =
    new CaseSteps(_).richCase.map[(String, JsObject)](c => c._id -> c.toJson.as[JsObject]).raw

  def taskToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[(String, JsObject)] =
    _.project(
      _.apply(By(new TaskSteps(__[Vertex]).richTask.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(caseToJson(db, graph)(__[Vertex].inTo[ShareTask].outTo[ShareCase])))
    ).map {
      case (task, (rootId, case0)) => rootId -> (task + ("case" -> case0))
    }

  def logToJson(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[(String, JsObject)] =
    _.project(
      _.apply(By(new LogSteps(__[Vertex]).richLog.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(taskToJson(db, graph)(__[Vertex].inTo[TaskLog])))
    ).map {
      case (log, (rootId, task)) => rootId -> (log + ("case_task" -> task))
    }

  def auditRenderer(implicit db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[(String, JsObject)] =
    (_: GremlinScala[Vertex])
      .coalesce(
        _.outTo[Audited]
          .choose[Label, (String, JsObject)](
            on = _.label(),
            BranchCase("Case", caseToJson),
            BranchCase("Task", taskToJson),
            BranchCase("Log", logToJson),
            BranchOtherwise(_.constant("" -> JsObject.empty))
          ),
        _.constant("" -> JsObject.empty)
      )

  val auditProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AuditSteps]
      .property("operation", UniMapping.string)(_.rename("action").readonly)
      .property("details", UniMapping.string)(_.simple.readonly)
      .property("objectType", UniMapping.string.optional)(_.simple.readonly)
      .property("objectId", UniMapping.string.optional)(_.simple.readonly)
      .property("base", UniMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UniMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UniMapping.string)(_.simple.readonly)
      .property("rootId", UniMapping.string)(_.derived(_.outTo[AuditContext].id().map(_.toString)).readonly)
      .build
}
