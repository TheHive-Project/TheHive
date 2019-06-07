package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import gremlin.scala.{__, By, Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services._
import org.thp.thehive.models.{ShareCase, ShareTask, TaskLog}
import org.thp.thehive.services._

@Singleton
class AuditCtrl @Inject()(
    entryPoint: EntryPoint,
    auditSrv: AuditSrv,
    val caseSrv: CaseSrv,
    val taskSrv: TaskSrv,
    val userSrv: UserSrv,
    implicit val db: Database
) extends AuditConversion
    with CaseConversion
    with TaskConversion
    with LogConversion {

  private def caseToJson(implicit graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[JsObject] =
    new CaseSteps(_).richCase.map[JsObject](_.toJson.as[JsObject]).raw

  private def taskToJson(implicit graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[JsObject] =
    _.project(
      _.apply(By(new TaskSteps(__[Vertex]).richTask.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(caseToJson(graph)(__[Vertex].inTo[ShareTask].outTo[ShareCase])))
    ).map {
      case (task, case0) ⇒ task + ("case" → case0)
    }

  private def logToJson(implicit graph: Graph): GremlinScala[Vertex] ⇒ GremlinScala[JsObject] =
    _.project(
      _.apply(By(new LogSteps(__[Vertex]).richLog.map[JsObject](_.toJson.as[JsObject]).raw))
        .and(By(taskToJson(graph)(__[Vertex].inTo[TaskLog])))
    ).map {
      case (log, task) ⇒ log + ("case_task" → task)
    }

  def flow(rootId: Option[String], count: Option[Int]): Action[AnyContent] =
    entryPoint("audit flow")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val audits = rootId
          .filterNot(_ == "any")
          .fold(auditSrv.initSteps)(rid ⇒ auditSrv.initSteps.forContext(rid))
          .visible
          .range(0, count.getOrElse(10).toLong)
          .richAuditWithCustomObjectRenderer {
            case "Case" ⇒ caseToJson
            case "Task" ⇒ taskToJson
            case "Log"  ⇒ logToJson
            case _      ⇒ _.constant(JsObject.empty)
          }
          .toList()
          .map {
            case (audit, obj) ⇒ audit.toJson.as[JsObject].deepMerge(Json.obj("base" → Json.obj("object" → obj)))
          }

        Success(Results.Ok(JsArray(audits)))
      }
}
