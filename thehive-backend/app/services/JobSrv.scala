package services

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
import org.elastic4play.services.{ Agg, AttachmentSrv, AuthContext, CreateSrv, DeleteSrv, EventSrv, FindSrv, GetSrv }
import org.elastic4play.services.{ QueryDef, UpdateSrv }
import org.elastic4play.services.JsonFormat.configWrites

import models.{ Artifact, ArtifactModel, Job, JobModel, JobStatus }

@Singleton
class JobSrv(analyzerConf: JsValue,
             artifactSrv: ArtifactSrv,
             analyzerSrv: AnalyzerSrv,
             jobModel: JobModel,
             createSrv: CreateSrv,
             getSrv: GetSrv,
             updateSrv: UpdateSrv,
             deleteSrv: DeleteSrv,
             findSrv: FindSrv,
             attachmentSrv: AttachmentSrv,
             eventSrv: EventSrv,
             implicit val ec: ExecutionContext) {
  @Inject def this(configuration: Configuration,
                   artifactSrv: ArtifactSrv,
                   analyzerSrv: AnalyzerSrv,
                   jobModel: JobModel,
                   createSrv: CreateSrv,
                   getSrv: GetSrv,
                   updateSrv: UpdateSrv,
                   deleteSrv: DeleteSrv,
                   findSrv: FindSrv,
                   attachmentSrv: AttachmentSrv,
                   eventSrv: EventSrv,
                   ec: ExecutionContext) =
    this(configWrites.writes(configuration.getConfig("analyzer.config").get),
      artifactSrv,
      analyzerSrv,
      jobModel,
      createSrv,
      getSrv,
      updateSrv,
      deleteSrv,
      findSrv,
      attachmentSrv,
      eventSrv,
      ec)

  lazy val log = Logger(getClass)

  def create(artifactId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    artifactSrv.get(artifactId).flatMap(a => create(a, fields))

  def create(artifact: Artifact, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    createSrv[JobModel, Job, Artifact](jobModel, artifact, fields.set("artifactId", artifact.id)).map {
      case job if job.status() == JobStatus.InProgress =>
        val newJob = for {
          analyzer <- analyzerSrv.get(job.analyzerId())
          (status, result) <- analyzer.analyze(attachmentSrv, artifact)
          updatedAttributes = Json.obj(
            "endDate" -> new Date(),
            "report" -> result.toString,
            "status" -> status)
          newJob <- updateSrv(job, Fields(updatedAttributes))
          _ = eventSrv.publish(StreamActor.Commit(authContext.requestId))
        } yield newJob
        newJob.onFailure {
          case t => log.error("Job execution fail", t)
        }
        job
      case job => job
    }
  }

  def create(artifactAndFields: Seq[(Artifact, Fields)])(implicit authContext: AuthContext) = {
    createSrv[JobModel, Job, Artifact](jobModel, artifactAndFields).map(
      _.zip(artifactAndFields).map {
        case (Success(job), _) if job.status() != JobStatus.InProgress => job
        case (Success(job), (artifact, _)) =>
          val newJob = for {
            analyzer <- analyzerSrv.get(job.analyzerId())
            (status, result) <- analyzer.analyze(attachmentSrv, artifact)
            updatedAttributes = Json.obj(
              "endDate" -> new Date(),
              "report" -> result.toString,
              "status" -> status)
            newJob <- updateSrv(job, Fields(updatedAttributes))
            _ = eventSrv.publish(StreamActor.Commit(authContext.requestId))
          } yield newJob
          newJob.onFailure {
            case t => log.error("Job execution fail", t)
          }
          job
      })
  }

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
