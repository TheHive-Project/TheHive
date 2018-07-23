package connectors.cortex.services

import java.util.Date

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scala.util.control.NonFatal

import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import akka.NotUsed
import akka.actor.{ Actor, ActorSystem }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models._
import javax.inject.{ Inject, Singleton }
import models.Artifact
import services.{ UserSrv ⇒ _, _ }

import org.elastic4play.controllers.Fields
import org.elastic4play.database.{ DBRemove, ModifyConfig }
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services._
import org.elastic4play.{ InternalError, NotFoundError }

@Singleton
class JobReplicateActor @Inject() (
    cortexSrv: CortexAnalyzerSrv,
    eventSrv: EventSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends Actor {

  private lazy val logger = Logger(getClass)

  override def preStart(): Unit = {
    eventSrv.subscribe(self, classOf[MergeArtifact])
    super.preStart()
  }

  override def postStop(): Unit = {
    eventSrv.unsubscribe(self)
    super.postStop()
  }

  override def receive: Receive = {
    case MergeArtifact(newArtifact, artifacts, authContext) ⇒
      logger.info(s"Merging jobs from artifacts ${artifacts.map(_.id)} into artifact ${newArtifact.id}")
      import org.elastic4play.services.QueryDSL._
      cortexSrv.find(and(parent("case_artifact", withId(artifacts.map(_.id): _*)), "status" ~= JobStatus.Success), Some("all"), Nil)._1
        .mapAsyncUnordered(5) { job ⇒
          val baseFields = Fields(job.attributes - "_id" - "_routing" - "_parent" - "_type" - "_version" - "createdBy" - "createdAt" - "updatedBy" - "updatedAt" - "user")
          val createdJob = cortexSrv.create(newArtifact, baseFields)(authContext)
          createdJob.failed.foreach(error ⇒ logger.error(s"Fail to create job under artifact ${newArtifact.id}\n\tjob attributes: $baseFields", error))
          createdJob
        }
        .runWith(Sink.ignore)
    case RemoveJobsOf(artifactId) ⇒
      import org.elastic4play.services.QueryDSL._
      cortexSrv.find(withParent("case_artifact", artifactId), Some("all"), Nil)._1
        .mapAsyncUnordered(5)(cortexSrv.realDeleteJob)
        .runWith(Sink.ignore)
  }
}

@Singleton
class CortexAnalyzerSrv @Inject() (
    cortexConfig: CortexConfig,
    jobModel: JobModel,
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    attachmentSrv: AttachmentSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    dbRemove: DBRemove,
    userSrv: UserSrv,
    implicit val system: ActorSystem,
    implicit val ws: WSClient,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  private[CortexAnalyzerSrv] lazy val logger = Logger(getClass)

  userSrv.inInitAuthContext { implicit authContext ⇒
    import org.elastic4play.services.QueryDSL._
    logger.info(s"Search for unfinished job ...")
    val (jobs, total) = find("status" ~= "InProgress", Some("all"), Nil)
    total.foreach(t ⇒ logger.info(s"$t jobs found"))
    jobs
      .runForeach { job ⇒
        logger.info(s"Found job in progress, request its status to Cortex")
        (for {
          cortexJobId ← job.cortexJobId()
          cortexClient ← cortexConfig.instances.find(_.name == job.cortexId)
        } yield updateJobWithCortex(job.id, cortexJobId, cortexClient))
          .getOrElse {
            val jobFields = Fields.empty
              .set("status", JobStatus.Failure.toString)
              .set("endDate", Json.toJson(new Date))
            update(job.id, jobFields)
          }
      }
  }

  private[services] def create(artifact: Artifact, fields: Fields)(implicit authContext: AuthContext): Future[Job] = {
    createSrv[JobModel, Job, Artifact](jobModel, artifact, fields.set("artifactId", artifact.id))
  }

  private[CortexAnalyzerSrv] def update(jobId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    update(jobId, fields, ModifyConfig.default)

  private[CortexAnalyzerSrv] def update(jobId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Job] =
    getJob(jobId).flatMap(job ⇒ update(job, fields, modifyConfig))

  private[CortexAnalyzerSrv] def update(job: Job, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    update(job, fields, ModifyConfig.default)

  private[CortexAnalyzerSrv] def update(job: Job, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Job] =
    updateSrv[Job](job, fields, modifyConfig)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Job, NotUsed], Future[Long]) = {
    findSrv[JobModel, Job](jobModel, queryDef, range, sortBy)
  }

  def realDeleteJob(job: Job): Future[Unit] = {
    dbRemove(job).map(_ ⇒ ())
  }

  def stats(query: QueryDef, aggs: Seq[Agg]) = findSrv(jobModel, query, aggs: _*)

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
        f(cortex).recover { case NonFatal(t) ⇒ logger.error("Request to Cortex fails", t); Nil }
      }
      .map(_.flatten)
  }

  def getAnalyzersFor(dataType: String): Future[Seq[Analyzer]] = {
    Future
      .traverse(cortexConfig.instances) { cortex ⇒
        cortex.listAnalyzerForType(dataType).recover { case NonFatal(t) ⇒ logger.error("Request to Cortex fails", t); Nil }
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
        cortex.listAnalyzer.recover { case NonFatal(t) ⇒ logger.error("Request to Cortex fails", t); Nil }
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

  def addImportFieldInArtifacts(job: JsObject): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    for {
      caze ← caseSrv.find(child("case_artifact", withId((job \ "_parent").as[String])), Some("0-1"), Nil)._1.runWith(Sink.headOption)
      updatedReport ← (job \ "report").asOpt[JsObject]
        .map { report ⇒
          val artifacts = for {
            artifact ← (report \ "artifacts").asOpt[Seq[JsObject]].getOrElse(Nil)
            dataType ← (artifact \ "dataType").asOpt[String]
            data ← (artifact \ "data").asOpt[String]
            foundArtifactId = artifactSrv.find(and(
              "data" ~= data,
              "dataType" ~= dataType,
              withParent(caze.get)), Some("0-1"), Nil)._1
              .runWith(Sink.headOption)
              .map(_.fold[JsValue](JsNull)(a ⇒ JsString(a.id)))
              .recover { case _ ⇒ JsNull }
          } yield foundArtifactId.map(faid ⇒ artifact + ("id" → faid))
          Future.sequence(artifacts).map(a ⇒ report + ("artifacts" → JsArray(a)))
        }
        .getOrElse(Future.successful(JsObject.empty))
    } yield job + ("report" → updatedReport)
  }

  def updateJobWithCortex(jobId: String, cortexJobId: String, cortex: CortexClient, maxError: Int = 3)(implicit authContext: AuthContext): Future[Job] = {
    def updateArtifactSummary(job: Job, report: String) = {
      Try(Json.parse(report))
        .toOption
        .flatMap(r ⇒ (r \ "summary").asOpt[JsObject])
        .map { jobSummary ⇒
          RetryOnError() {
            for {
              artifact ← artifactSrv.get(job.artifactId())
              reports = Try(Json.parse(artifact.reports()).asOpt[JsObject]).toOption.flatten.getOrElse(JsObject.empty)
              newReports = reports + (job.analyzerDefinition().getOrElse(job.analyzerId()) → jobSummary)
              _ ← artifactSrv.update(job.artifactId(), Fields.empty.set("reports", newReports.toString), ModifyConfig(retryOnConflict = 0, version = Some(artifact.version)))
            } yield ()
          }
            .recover {
              case NonFatal(t) ⇒ logger.warn(s"Unable to insert summary report in artifact", t)
            }
        }
        .getOrElse(Future.successful(()))
    }

    logger.debug(s"Requesting status of job $cortexJobId in cortex ${cortex.name} in order to update job $jobId")
    cortex.waitReport(cortexJobId, 1.minute).flatMap { j ⇒
      val status = (j \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.Failure)
      if (status == JobStatus.InProgress || status == JobStatus.Waiting)
        updateJobWithCortex(jobId, cortexJobId, cortex)
      else {
        val report = (j \ "report").asOpt[JsObject].getOrElse(JsObject.empty).toString
        logger.debug(s"Job $cortexJobId in cortex ${cortex.name} has finished with status $status, updating job $jobId")
        val updatedJob = for {
          job ← getSrv[JobModel, Job](jobModel, jobId)
          jobFields = Fields.empty
            .set("status", status.toString)
            .set("report", report)
            .set("endDate", Json.toJson(new Date))
          updatedJob ← update(job, jobFields)
          _ ← if (status == JobStatus.Success) updateArtifactSummary(job, report) else Future.successful(())
        } yield updatedJob
        updatedJob.failed.foreach(logger.error(s"Update job fails", _))
        updatedJob
      }
    }
      .recoverWith {
        case CortexError(404, _, _) ⇒
          logger.debug(s"The job $cortexJobId not found")
          val jobFields = Fields.empty
            .set("status", JobStatus.Failure.toString)
            .set("endDate", Json.toJson(new Date))
          update(jobId, jobFields)
        case _ if maxError > 0 ⇒
          logger.debug(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails, restarting ...")
          updateJobWithCortex(jobId, cortexJobId, cortex, maxError - 1)
        case _ ⇒
          logger.error(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails and the number of errors reaches the limit, aborting")
          update(jobId, Fields.empty
            .set("status", JobStatus.Failure.toString)
            .set("endDate", Json.toJson(new Date)))
      }
  }

  def submitJob(cortexId: Option[String], analyzerName: String, artifactId: String)(implicit authContext: AuthContext): Future[Job] = {
    val cortexClientAnalyzer = cortexId match {
      case Some(id) ⇒
        cortexConfig
          .instances
          .find(_.name == id)
          .fold[Future[(CortexClient, Analyzer)]](Future.failed(NotFoundError(s"cortex $id not found"))) { c ⇒
            c.getAnalyzer(analyzerName)
              .map(c → _)
          }

      case None ⇒
        Future.firstCompletedOf {
          cortexConfig.instances.map(c ⇒ c.getAnalyzer(analyzerName).map(c → _))
        }
    }

    cortexClientAnalyzer.flatMap {
      case (cortex, analyzer) ⇒
        for {
          artifact ← artifactSrv.get(artifactId)
          caze ← caseSrv.get(artifact.parentId.get)
          artifactAttributes = Json.obj(
            "tlp" → artifact.tlp(),
            "pap" → caze.pap(),
            "dataType" → artifact.dataType(),
            "message" → caze.caseId().toString)
          cortexArtifact = (artifact.data(), artifact.attachment()) match {
            case (Some(data), None)       ⇒ DataArtifact(data, artifactAttributes)
            case (None, Some(attachment)) ⇒ FileArtifact(attachmentSrv.source(attachment.id), artifactAttributes + ("attachment" → Json.toJson(attachment)))
            case _                        ⇒ throw InternalError(s"Artifact has invalid data : ${artifact.attributes}")
          }
          cortexJobJson ← cortex.analyze(analyzer.id, cortexArtifact)
          cortexJob = cortexJobJson.as[CortexJob]
          job ← create(artifact, Fields.empty
            .set("analyzerId", cortexJob.workerId)
            .set("analyzerName", cortexJob.workerName)
            .set("analyzerDefinition", cortexJob.workerDefinition)
            .set("artifactId", artifactId)
            .set("cortexId", cortex.name)
            .set("cortexJobId", cortexJob.id))
          _ = updateJobWithCortex(job.id, cortexJob.id, cortex)
        } yield job
    }
  }
}
