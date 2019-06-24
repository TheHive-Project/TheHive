package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.InputJob
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.Query
import org.thp.thehive.connector.cortex.services.JobSrv
import org.thp.thehive.controllers.v0.QueryCtrl
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class JobCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    val queryExecutor: CortexQueryExecutor,
    jobSrv: JobSrv,
    observableSrv: ObservableSrv
) extends QueryCtrl
    with JobConversion {

  def get(jobId: String): Action[AnyContent] =
    entryPoint("get job")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        jobSrv
          .get(jobId)
          .visible
          .getOrFail()
          .map { job ⇒
            Results.Ok(job.toJson)
          }
      }

  def search: Action[AnyContent] =
    entryPoint("search job")
      .extract('query, searchParser("listJob"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val query: Query = request.body('query)
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                          ⇒ Success(resp)
        }
      }

  def create(): Action[AnyContent] =
    entryPoint("create job")
      .extract('job, FieldsParser[InputJob])
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val inputJob: InputJob = request.body('job)
        for {
          observable ← observableSrv.getOrFail(inputJob.artifactId)
          job        ← Try(jobSrv.create(inputJob, observable))
        } yield Results.Created(job.toJson)
      }
}
