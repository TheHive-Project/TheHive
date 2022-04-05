package org.thp.thehive.connector.cortex.controllers.v0

import com.google.inject.name.Named
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AuthorizationError, EntityIdOrName, ErrorHandler}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models.{Job, RichJob}
import org.thp.thehive.connector.cortex.services.JobOps._
import org.thp.thehive.connector.cortex.services.JobSrv
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v0.{OutputParam, PublicData, QueryCtrl}
import org.thp.thehive.models.{Observable, Permissions, RichCase, RichObservable}
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JobCtrl @Inject() (
    override val entrypoint: Entrypoint,
    override val db: Database,
    jobSrv: JobSrv,
    observableSrv: ObservableSrv,
    errorHandler: ErrorHandler,
    implicit val ec: ExecutionContext,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicJob
) extends QueryCtrl {
  def get(jobId: String): Action[AnyContent] =
    entrypoint("get job")
      .authRoTransaction(db) { implicit request => implicit graph =>
        jobSrv
          .get(EntityIdOrName(jobId))
          .visible
          .richJob
          .getOrFail("Job")
          .map(job => Results.Ok(job.toJson))
      }

  def create: Action[AnyContent] =
    entrypoint("create job")
      .extract("analyzerId", FieldsParser[String].on("analyzerId"))
      .extract("cortexId", FieldsParser[String].on("cortexId"))
      .extract("artifactId", FieldsParser[String].on("artifactId"))
      .extract("parameters", FieldsParser.jsObject.optional.on("parameters"))
      .asyncAuth { implicit request =>
        if (request.isPermitted(Permissions.manageAnalyse)) {
          val analyzerId: String           = request.body("analyzerId")
          val cortexId: String             = request.body("cortexId")
          val parameters: Option[JsObject] = request.body("parameters")
          db.roTransaction { implicit graph =>
            val artifactId: String = request.body("artifactId")
            for {
              o <- observableSrv.get(EntityIdOrName(artifactId)).can(Permissions.manageAnalyse).richObservable.getOrFail("Observable")
              c <- observableSrv.get(EntityIdOrName(artifactId)).`case`.getOrFail("Case")
            } yield (o, c)
          }.fold(
            error => errorHandler.onServerError(request, error),
            {
              case (o, c) =>
                jobSrv
                  .submit(cortexId, analyzerId, o, c, parameters.getOrElse(JsObject.empty))
                  .map(j => Results.Created(j.toJson))
            }
          )
        } else Future.failed(AuthorizationError("Job creation not allowed"))
      }
}

@Singleton
class PublicJob @Inject() (jobSrv: JobSrv) extends PublicData with JobRenderer {
  override val entityName: String = "job"
  override val initialQuery: Query =
    Query.init[Traversal.V[Job]]("listJob", (graph, authContext) => jobSrv.startTraversal(graph).visible(authContext))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Job]](
    "getJob",
    (idOrName, graph, authContext) => jobSrv.get(idOrName)(graph).visible(authContext)
  )
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Job], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, _, withParents), jobSteps, authContext) if withParents > 0 =>
          jobSteps.richPage(from, to, withTotal = true, limitedCountThreshold)(_.richJobWithCustomRenderer(jobParents(_)(authContext))(authContext))
        case (range, jobSteps, authContext) =>
          jobSteps.richPage(range.from, range.to, withTotal = true, limitedCountThreshold)(
            _.richJob(authContext).domainMap((_, None: Option[(RichObservable, RichCase)]))
          )
      }
    )
  override val outputQuery: Query = Query.outputWithContext[RichJob, Traversal.V[Job]]((jobSteps, authContext) => jobSteps.richJob(authContext))
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Observable], Traversal.V[Job]]("jobs", (observables, _) => observables.jobs)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Job]
    .property("analyzerId", UMapping.string)(_.rename("workerId").readonly)
    .property("cortexId", UMapping.string.optional)(_.field.readonly)
    .property("startDate", UMapping.date)(_.field.readonly)
    .property("status", UMapping.string)(_.field.readonly)
    .property("analyzerDefinition", UMapping.string)(_.rename("workerDefinition").readonly)
    .build
}
