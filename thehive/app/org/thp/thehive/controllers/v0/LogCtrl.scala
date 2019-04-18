package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, ResultWithTotalSize}
import org.thp.scalligraph.query.Query
import org.thp.thehive.dto.v0.InputLog
import org.thp.thehive.services.{LogSrv, TaskSrv}

@Singleton
class LogCtrl @Inject()(entryPoint: EntryPoint, db: Database, logSrv: LogSrv, taskSrv: TaskSrv, val queryExecutor: TheHiveQueryExecutor)
    extends QueryCtrl
    with LogConversion {
  lazy val logger = Logger(getClass)

  def create(taskId: String): Action[AnyContent] =
    entryPoint("create log")
      .extract('log, FieldsParser[InputLog])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val inputLog: InputLog = request.body('log)
          taskSrv.getOrFail(taskId).map { task ⇒
            val createdObservable = logSrv.create(inputLog, task)
            Results.Created(createdObservable.toJson)
          }
        }
      }

  def stats(): Action[AnyContent] = {
    val parser: FieldsParser[Seq[Query]] = statsParser("listLog")
    entryPoint("stats log")
      .extract('query, parser)
      .authenticated { implicit request ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map { query ⇒
            db.transaction { graph ⇒
              queryExecutor.execute(query, graph, request.authContext).toJson
            }
          }
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }
  }

  def search: Action[AnyContent] =
    entryPoint("search log")
      .extract('query, searchParser("listLog", paged = false))
      .authenticated { implicit request ⇒
        val query: Query = request.body('query)
        val result = db.transaction { graph ⇒
          queryExecutor.execute(query, graph, request.authContext)
        }
        val resp = (result.toJson \ "result").as[JsArray]
        result.toOutput match {
          case ResultWithTotalSize(_, size) ⇒ Success(Results.Ok(resp).withHeaders("X-Total" → size.toString))
          case _                            ⇒ Success(Results.Ok(resp).withHeaders("X-Total" → resp.value.size.toString))
        }
      }
}
