package connectors.cortex.services

import java.nio.file.{ Path, Paths }

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorDSL.{ Act, actor }
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import play.api.{ Configuration, Logger }
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient

import org.elastic4play.{ InternalError, NotFoundError }
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AttachmentSrv, AuthContext, CreateSrv, EventSrv, FindSrv, GetSrv, QueryDef, UpdateSrv }

import connectors.cortex.models.{ Analyzer, CortexJob, CortexModel, DataArtifact, FileArtifact, Job, JobModel, JobStatus }
import connectors.cortex.models.JsonFormat.{ cortexJobFormat, jobStatusFormat }
import models.Artifact
import services.{ ArtifactSrv, MergeArtifact }

object CortexConfig {
  def getCortexClient(name: String, configuration: Configuration): Option[CortexClient] = {
    try {
      val url = configuration.getString("url").getOrElse(sys.error("url is missing")).replaceFirst("/*$", "")
      val key = configuration.getString("key").getOrElse(sys.error("key is missing"))
      Some(new CortexClient(name, url, key))
    }
    catch {
      case NonFatal(_) ⇒ None
    }
  }

  def getInstances(configuration: Configuration): Map[String, CortexClient] = {
    val instances = for {
      cfg ← configuration.getConfig("cortex").toSeq
      key ← cfg.subKeys
      c ← cfg.getConfig(key)
      cic ← getCortexClient(key, c)
    } yield key → cic
    instances.toMap
  }
}
case class CortexConfig(truststore: Option[Path], instances: Map[String, CortexClient]) {

  @Inject
  def this(configuration: Configuration) = this(
    configuration.getString("cortex.cert").map(p ⇒ Paths.get(p)),
    CortexConfig.getInstances(configuration))
}

@Singleton
class CortexSrv @Inject() (
    cortexConfig: CortexConfig,
    jobModel: JobModel,
    artifactSrv: ArtifactSrv,
    attachmentSrv: AttachmentSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    eventSrv: EventSrv,
    implicit val ws: WSClient,
    implicit val ec: ExecutionContext,
    implicit val system: ActorSystem,
    implicit val mat: Materializer) {

  lazy val log = Logger(getClass)

  val mergeActor = actor(new Act {
    become {
      case MergeArtifact(newArtifact, artifacts, authContext) ⇒
        import org.elastic4play.services.QueryDSL._
        find(and(parent("case_artifact", withId(artifacts.map(_.id): _*)), "status" ~= JobStatus.Success), Some("all"), Nil)._1
          .mapAsyncUnordered(5) { job ⇒
            val baseFields = Fields(job.attributes - "_id" - "_routing" - "_parent" - "_type" - "createdBy" - "createdAt" - "updatedBy" - "updatedAt" - "user")
            create(newArtifact, baseFields)(authContext)
          }
          .runWith(Sink.ignore)
    }
  })

  eventSrv.subscribe(mergeActor, classOf[MergeArtifact]) // need to unsubsribe ?

  private[CortexSrv] def create(artifact: Artifact, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    createSrv[JobModel, Job, Artifact](jobModel, artifact, fields.set("artifactId", artifact.id))
  }

  private[CortexSrv] def update(id: String, fields: Fields)(implicit Context: AuthContext) = {
    updateSrv[JobModel, Job](jobModel, id, fields)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def askAllCortex[A](f: CortexClient ⇒ Future[CortexModel[A]]): Future[Seq[A]] = {
    Future.traverse(cortexConfig.instances.toSeq) {
      case (name, cortex) ⇒ f(cortex).map(_.onCortex(name))
    }
  }
  def askForAllCortex[A](f: CortexClient ⇒ Future[Seq[CortexModel[A]]]): Future[Seq[A]] = {
    Future
      .traverse(cortexConfig.instances.toSeq) {
        case (name, cortex) ⇒ f(cortex).map(_.map(_.onCortex(name)))
      }
      .map(_.flatten)
  }
  def getAnalyzer(analyzerId: String): Future[Seq[Analyzer]] = {
    askAllCortex(_.getAnalyzer(analyzerId))
  }

  def getAnalyzersFor(dataType: String): Future[Seq[Analyzer]] = {
    askForAllCortex(_.listAnalyzerForType(dataType))
  }

  def listAnalyzer: Future[Seq[Analyzer]] = {
    askForAllCortex(_.listAnalyzer)
  }

  def getJob(jobId: String): Future[Job] = {
    getSrv[JobModel, Job](jobModel, jobId)
  }

  def updateJobWithCortex(jobId: String, cortexJobId: String, cortex: CortexClient)(implicit authContext: AuthContext): Unit = {
    log.debug(s"Requesting status of job $cortexJobId in cortex ${cortex.name} in order to update job ${jobId}")
    cortex.waitReport(cortexJobId, Duration.Inf) andThen {
      case Success(j) ⇒
        val status = (j \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.Failure)
        val report = (j \ "report").as[JsObject]
        log.debug(s"Job $cortexJobId in cortex ${cortex.name} has finished with status $status, updating job ${jobId}")
        update(jobId, Fields.empty.set("status", status.toString).set("report", report))
      case Failure(e) ⇒
        log.debug(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails, restarting ...")
        updateJobWithCortex(jobId, cortexJobId, cortex)
    }
    ()
  }

  def submitJob(cortexId: String, analyzerId: String, artifactId: String)(implicit authContext: AuthContext): Future[Job] = {
    cortexConfig.instances.get(cortexId).fold[Future[Job]](Future.failed(NotFoundError(s"Cortex $cortexId not found"))) { cortex ⇒

      for {
        artifact ← artifactSrv.get(artifactId)
        cortexArtifact = (artifact.data(), artifact.attachment()) match {
          case (Some(data), None)       ⇒ DataArtifact(data, artifact.attributes)
          case (None, Some(attachment)) ⇒ FileArtifact(attachmentSrv.source(attachment.id), artifact.attributes)
          case _                        ⇒ throw InternalError(s"Artifact has invalid data : ${artifact.attributes}")
        }
        cortexJobJson ← cortex.analyze(analyzerId, cortexArtifact)
        cortexJob = cortexJobJson.as[CortexJob]
        job ← create(artifact, Fields.empty
          .set("analyzerId", cortexJob.analyzerId)
          .set("artifactId", artifactId)
          .set("cortexId", cortex.name)
          .set("cortexJobId", cortexJob.id))
        _ = updateJobWithCortex(job.id, cortexJob.id, cortex)
      } yield job
    }
  }
}