package connectors.cortex.services

import java.nio.file.{ Path, Paths }
import java.util.Date
import javax.inject.{ Inject, Singleton }

import akka.NotUsed
import akka.actor.ActorDSL.{ Act, actor }
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models._
import models.Artifact
import org.elastic4play.controllers.Fields
import org.elastic4play.services._
import org.elastic4play.{ InternalError, NotFoundError }
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }
import services.{ ArtifactSrv, CustomWSAPI, MergeArtifact }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

object CortexConfig {
  def getCortexClient(name: String, configuration: Configuration, ws: CustomWSAPI): Option[CortexClient] = {
    try {
      val url = configuration.getString("url").getOrElse(sys.error("url is missing")).replaceFirst("/*$", "")
      val key = "" // configuration.getString("key").getOrElse(sys.error("key is missing"))
      Some(new CortexClient(name, url, key, ws))
    }
    catch {
      case NonFatal(e) ⇒
        Logger.error("Error while loading cortex configuration", e)
        None
    }
  }

  def getInstances(configuration: Configuration, globalWS: CustomWSAPI): Seq[CortexClient] = {
    for {
      cfg ← configuration.getConfig("cortex").toSeq
      cortexWS = globalWS.withConfig(cfg)
      key ← cfg.subKeys
      if key != "ws"
      c ← cfg.getConfig(key)
      instanceWS = cortexWS.withConfig(c)
      cic ← getCortexClient(key, c, instanceWS)
    } yield cic
  }
}

@Singleton
case class CortexConfig(truststore: Option[Path], instances: Seq[CortexClient]) {

  @Inject
  def this(configuration: Configuration, globalWS: CustomWSAPI) = this(
    configuration.getString("cortex.cert").map(p ⇒ Paths.get(p)),
    CortexConfig.getInstances(configuration, globalWS))
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

  lazy val logger = Logger(getClass)

  private[CortexSrv] val mergeActor = actor(new Act {
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

  private[CortexSrv] def update(job: Job, fields: Fields)(implicit Context: AuthContext) = {
    updateSrv[Job](job, fields)
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def getAnalyzer(analyzerId: String): Future[Analyzer] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        cortex.getAnalyzer(analyzerId).map(Some(_)).fallbackTo(Future.successful(None))
      }
      .map { analyzers ⇒
        analyzers
          .foldLeft[Option[Analyzer]](None) {
            case (Some(analyzer1), Some(analyzer2)) ⇒ Some(analyzer1.copy(cortexIds = analyzer1.cortexIds ++ analyzer2.cortexIds))
            case (maybeAnalyzer1, maybeAnalyzer2)   ⇒ maybeAnalyzer1 orElse maybeAnalyzer2
          }
          .getOrElse(throw NotFoundError(s"Analyzer $analyzerId not found"))
      }
  }

  def askAnalyzersOnAllCortex(f: CortexClient ⇒ Future[Seq[Analyzer]]): Future[Seq[Analyzer]] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        f(cortex)
      }
      .map(_.flatten)
  }

  def getAnalyzersFor(dataType: String): Future[Seq[Analyzer]] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        cortex.listAnalyzerForType(dataType)
      }
      .map { listOfListOfAnalyzers ⇒
        val analysers = listOfListOfAnalyzers.flatten
        analysers
          .groupBy(_.name)
          .values
          .map(_.reduce((a1, a2) ⇒ a1.copy(cortexIds = a1.cortexIds ::: a2.cortexIds)))
          .toSeq
      }
  }

  def listAnalyzer: Future[Seq[Analyzer]] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        cortex.listAnalyzer
      }
      .map { listOfListOfAnalyzers ⇒
        val analysers = listOfListOfAnalyzers.flatten
        analysers
          .groupBy(_.name)
          .values
          .map(_.reduceLeft((a1, a2) ⇒ a1.copy(cortexIds = a1.cortexIds ::: a2.cortexIds)))
          .toSeq
      }
  }

  def getJob(jobId: String): Future[Job] = {
    getSrv[JobModel, Job](jobModel, jobId)
  }

  def updateJobWithCortex(jobId: String, cortexJobId: String, cortex: CortexClient)(implicit authContext: AuthContext): Unit = {
    logger.debug(s"Requesting status of job $cortexJobId in cortex ${cortex.name} in order to update job $jobId")
    cortex.waitReport(cortexJobId, 1.minute) andThen {
      case Success(j) ⇒
        val status = (j \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.Failure)
        if (status == JobStatus.InProgress)
          updateJobWithCortex(jobId, cortexJobId, cortex)
        else {
          val report = (j \ "report").asOpt[JsObject].getOrElse(JsObject(Nil)).toString
          logger.debug(s"Job $cortexJobId in cortex ${cortex.name} has finished with status $status, updating job $jobId")
          getSrv[JobModel, Job](jobModel, jobId)
            .flatMap { job ⇒
              if (status == JobStatus.Success) {
                val jobSummary = Try(Json.parse(report))
                  .toOption
                  .flatMap(r ⇒ (r \ "summary").asOpt[JsObject])
                  .getOrElse(JsObject(Nil))
                for {
                  artifact ← artifactSrv.get(job.artifactId())
                  reports = Try(Json.parse(artifact.reports()).asOpt[JsObject]).toOption.flatten.getOrElse(JsObject(Nil))
                  newReports = reports + (job.analyzerId() → jobSummary)
                } artifactSrv.update(job.artifactId(), Fields.empty.set("reports", newReports.toString))
                  .onComplete {
                    case Failure(t) ⇒ logger.warn(s"Unable to insert summary report in artifact", t)
                    case Success(_) ⇒
                  }
              }
              val jobFields = Fields.empty
                .set("status", status.toString)
                .set("report", report)
                .set("endDate", Json.toJson(new Date))
              update(job, jobFields)
            }
            .onComplete {
              case Failure(e) ⇒ logger.error(s"Update job fails", e)
              case _          ⇒
            }
        }
      case Failure(_) ⇒
        logger.debug(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails, restarting ...")
        updateJobWithCortex(jobId, cortexJobId, cortex)
    }
    ()
  }

  def submitJob(cortexId: Option[String], analyzerId: String, artifactId: String)(implicit authContext: AuthContext): Future[Job] = {
    val cortexClient = cortexId match {
      case Some(id) ⇒ Future.successful(cortexConfig.instances.find(_.name == id))
      case None ⇒ if (cortexConfig.instances.size <= 1) Future.successful(cortexConfig.instances.headOption)
      else {
        Future // If there are several cortex, select the first which has the analyzer
          .traverse(cortexConfig.instances)(c ⇒ c.getAnalyzer(analyzerId).map(_ ⇒ Some(c)).recover { case _ ⇒ None })
          .map(_.flatten.headOption)
      }
    }

    cortexClient.flatMap {
      case Some(cortex) ⇒
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
      case None ⇒ Future.failed(NotFoundError(s"Cortex $cortexId not found"))
    }
  }
}