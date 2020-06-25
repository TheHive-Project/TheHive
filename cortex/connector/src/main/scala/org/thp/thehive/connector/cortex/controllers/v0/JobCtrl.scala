package org.thp.thehive.connector.cortex.controllers.v0

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AuthorizationError, ErrorHandler}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models.RichJob
import org.thp.thehive.connector.cortex.services.{JobSrv, JobSteps}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v0.{IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.ObservableSrv
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JobCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-cortex-schema") db: Database,
    properties: Properties,
    jobSrv: JobSrv,
    observableSrv: ObservableSrv,
    errorHandler: ErrorHandler,
    implicit val ec: ExecutionContext
) extends QueryableCtrl {
  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "job"
  override val publicProperties: List[PublicProperty[_, _]] = properties.job ::: metaProperties[JobSteps]
  override val initialQuery: Query =
    Query.init[JobSteps]("listJob", (graph, authContext) => jobSrv.initSteps(graph).visible(authContext))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, JobSteps](
    "getJob",
    FieldsParser[IdOrName],
    (param, graph, authContext) => jobSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, JobSteps, PagedResult[RichJob]](
    "page",
    FieldsParser[OutputParam],
    (range, jobSteps, authContext) => jobSteps.richPage(range.from, range.to, withTotal = true)(_.richJob(authContext))
  )
  override val outputQuery: Query = Query.outputWithContext[RichJob, JobSteps]((jobSteps, authContext) => jobSteps.richJob(authContext))

  def get(jobId: String): Action[AnyContent] =
    entrypoint("get job")
      .authRoTransaction(db) { implicit request => implicit graph =>
        jobSrv
          .getByIds(jobId)
          .visible
          .richJob
          .getOrFail()
          .map(job => Results.Ok(job.toJson))
      }

  def create: Action[AnyContent] =
    entrypoint("create job")
      .extract("analyzerId", FieldsParser[String].on("analyzerId"))
      .extract("cortexId", FieldsParser[String].on("cortexId"))
      .extract("artifactId", FieldsParser[String].on("artifactId"))
      .asyncAuth { implicit request =>
        if (request.isPermitted(Permissions.manageAnalyse)) {
          val analyzerId: String = request.body("analyzerId")
          val cortexId: String   = request.body("cortexId")
          db.roTransaction { implicit graph =>
              val artifactId: String = request.body("artifactId")
              for {
                o <- observableSrv.getByIds(artifactId).richObservable.getOrFail("Observable")
                c <- observableSrv.getByIds(artifactId).`case`.getOrFail("Case")
              } yield (o, c)
            }
            .fold(error => errorHandler.onServerError(request, error), {
              case (o, c) =>
                jobSrv
                  .submit(cortexId, analyzerId, o, c)
                  .map(j => Results.Created(j.toJson))
            })
        } else Future.failed(AuthorizationError("Job creation not allowed"))
      }
}
