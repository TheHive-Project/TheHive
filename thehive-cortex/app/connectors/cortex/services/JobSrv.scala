package connectors.cortex.services

import java.util.Date

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

import akka.NotUsed
import akka.stream.scaladsl.Source

import play.api.{ Configuration, Logger }
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Agg, AttachmentSrv, AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv }
import org.elastic4play.services.{ QueryDef, UpdateSrv }
import org.elastic4play.services.JsonFormat.configWrites

import models.{ Artifact, ArtifactModel }
import services.ArtifactSrv
import connectors.cortex.models.JobModel
import connectors.cortex.models.Job

@Singleton
class JobSrv(
    analyzerConf: JsValue,
    artifactSrv: ArtifactSrv,
    jobModel: JobModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    attachmentSrv: AttachmentSrv,
    implicit val ec: ExecutionContext) {
  @Inject def this(
    configuration: Configuration,
    artifactSrv: ArtifactSrv,
    //    analyzerSrv: AnalyzerSrv,
    jobModel: JobModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    attachmentSrv: AttachmentSrv,
    ec: ExecutionContext) =
    this(
      configWrites.writes(configuration.getConfig("analyzer.config").get),
      artifactSrv,
      //      analyzerSrv,
      jobModel,
      createSrv,
      getSrv,
      updateSrv,
      deleteSrv,
      findSrv,
      attachmentSrv,
      ec)

  lazy val log = Logger(getClass)

  def create(artifactId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    artifactSrv.get(artifactId).flatMap(a ⇒ create(a, fields))

  def create(artifact: Artifact, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    createSrv[JobModel, Job, Artifact](jobModel, artifact, fields.set("artifactId", artifact.id))
    ???
  }
  //[ M <: org.elastic4play.models.ChildModelDef[M, E, _, PE],
  //  E <: org.elastic4play.models.EntityDef[M,E],
  //  PE <: org.elastic4play.models.BaseEntity](model: M, parent: PE, fields: org.elastic4play.controllers.Fields)(implicit authContext: org.elastic4play.services.AuthContext)scala.concurrent.Future[E]
  //  def create(artifactAndFields: Seq[(Artifact, Fields)])(implicit authContext: AuthContext) = {
  //    createSrv[JobModel, Job, Artifact](jobModel, artifactAndFields).map(
  //      _.zip(artifactAndFields).map {
  //        case (Success(job), _) if job.status() != JobStatus.InProgress ⇒ job
  //        case (Success(job), (artifact, _)) ⇒
  //          val newJob = for {
  //            analyzer ← analyzerSrv.get(job.analyzerId())
  //            (status, result) ← analyzer.analyze(attachmentSrv, artifact)
  //            updatedAttributes = Json.obj(
  //              "endDate" → new Date(),
  //              "report" → result.toString,
  //              "status" → status)
  //            newJob ← updateSrv(job, Fields(updatedAttributes))
  //            _ = eventSrv.publish(StreamActor.Commit(authContext.requestId))
  //          } yield newJob
  //          newJob.onFailure {
  //            case t ⇒ log.error("Job execution fail", t)
  //          }
  //          job
  //      })
  //  }

  def get(id: String)(implicit Context: AuthContext) =
    getSrv[JobModel, Job](jobModel, id)

  def update(id: String, fields: Fields)(implicit Context: AuthContext) =
    updateSrv[JobModel, Job](jobModel, id, fields)

  def delete(id: String)(implicit Context: AuthContext) =
    deleteSrv[JobModel, Job](jobModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, agg: Agg) = findSrv(jobModel, queryDef, agg)
}
