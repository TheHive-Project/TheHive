package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.Query
import org.thp.thehive.connector.cortex.services.JobSrv
import org.thp.thehive.controllers.v0.QueryCtrl
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

@Singleton
class JobCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    val queryExecutor: CortexQueryExecutor,
    jobSrv: JobSrv,
    observableSrv: ObservableSrv
) extends QueryCtrl {
  import JobConversion._

  def get(jobId: String): Action[AnyContent] =
    entryPoint("get job")
      .authTransaction(db) { implicit request => implicit graph =>
        jobSrv
          .get(jobId)
          .visible
          .getOrFail()
          .map { job =>
            Results.Ok(job.toJson)
          }
      }

  def search: Action[AnyContent] =
    entryPoint("search job")
      .extract("query", searchParser("listJob"))
      .authTransaction(db) { implicit request => graph =>
        val query: Query = request.body("query")
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) => Success(resp.withHeaders("X-Total" -> size.toString))
          case _                          => Success(resp)
        }
      }

  def create(): Action[AnyContent] =
    entryPoint("create job")
      .extract("analyzerId", FieldsParser[String].on("analyzerId"))
      .extract("cortexId", FieldsParser[String].on("cortexId"))
      .extract("artifactId", FieldsParser[String].on("artifactId"))
      .asyncAuth { implicit request =>
        db.transaction { implicit graph =>
          val analyzerId: String = request.body("analyzerId")
          val cortexId: String   = request.body("cortexId")
          val artifactId: String = request.body("artifactId")
          val tryObservable      = observableSrv.get(artifactId).richObservable.getOrFail()
          val tryCase            = observableSrv.get(artifactId).`case`.getOrFail()
          val r = for {
            o <- tryObservable
            c <- tryCase
          } yield jobSrv
            .submit(cortexId, analyzerId, o, c)
            .map(j => Results.Created(j.toJson))

          r.getOrElse(Future.successful(Results.InternalServerError))
        }
      }
}
