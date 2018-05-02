package connectors.cortex.services

import java.util.Date

import javax.inject.{ Inject, Singleton }
import akka.NotUsed
import akka.actor.Actor
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.cortex.models.JsonFormat._
import connectors.cortex.models._
import models.Artifact

import org.elastic4play.controllers.Fields
import org.elastic4play.services._
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.{ InternalError, NotFoundError }
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Logger }

import services.{ ArtifactSrv, CustomWSAPI, MergeArtifact, RemoveJobsOf }
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

import org.elastic4play.database.{ DBRemove, ModifyConfig }

object CortexConfig {
  def getCortexClient(name: String, configuration: Configuration, ws: CustomWSAPI): Option[CortexClient] = {
    val url = configuration.getOptional[String]("url").getOrElse(sys.error("url is missing")).replaceFirst("/*$", "")
    val authentication =
      configuration.getOptional[String]("key").map(CortexAuthentication.Key)
        .orElse {
          for {
            basicEnabled ← configuration.getOptional[Boolean]("basicAuth")
            if basicEnabled
            username ← configuration.getOptional[String]("username")
            password ← configuration.getOptional[String]("password")
          } yield CortexAuthentication.Basic(username, password)
        }
    Some(new CortexClient(name, url, authentication, ws))
  }

  def getInstances(configuration: Configuration, globalWS: CustomWSAPI): Seq[CortexClient] = {
    for {
      cfg ← configuration.getOptional[Configuration]("cortex").toSeq
      cortexWS = globalWS.withConfig(cfg)
      key ← cfg.subKeys
      if key != "ws"
      c ← cfg.getOptional[Configuration](key)
      instanceWS = cortexWS.withConfig(c)
      cic ← getCortexClient(key, c, instanceWS)
    } yield cic
  }
}

@Singleton
case class CortexConfig(instances: Seq[CortexClient]) {

  @Inject
  def this(configuration: Configuration, globalWS: CustomWSAPI) = this(
    CortexConfig.getInstances(configuration, globalWS))
}

@Singleton
class JobReplicateActor @Inject() (
    cortexSrv: CortexSrv,
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
class CortexSrv @Inject() (
    cortexConfig: CortexConfig,
    jobModel: JobModel,
    artifactSrv: ArtifactSrv,
    attachmentSrv: AttachmentSrv,
    getSrv: GetSrv,
    createSrv: CreateSrv,
    updateSrv: UpdateSrv,
    findSrv: FindSrv,
    dbRemove: DBRemove,
    userSrv: UserSrv,
    implicit val ws: WSClient,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  private[CortexSrv] lazy val logger = Logger(getClass)

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

  private[CortexSrv] def update(jobId: String, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    update(jobId, fields, ModifyConfig.default)

  private[CortexSrv] def update(jobId: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Job] =
    getJob(jobId).flatMap(job ⇒ update(job, fields, modifyConfig))

  private[CortexSrv] def update(job: Job, fields: Fields)(implicit authContext: AuthContext): Future[Job] =
    update(job, fields, ModifyConfig.default)

  private[CortexSrv] def update(job: Job, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Job] =
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

  def retryIf[A](f: Throwable ⇒ Boolean, maxRetry: Int)(body: ⇒ Future[A]): Future[A] = {
    body.recoverWith {
      case e if maxRetry > 0 && f(e) ⇒ retryIf(f, maxRetry - 1)(body)
    }
  }

  def updateJobWithCortex(jobId: String, cortexJobId: String, cortex: CortexClient)(implicit authContext: AuthContext): Unit = {
    logger.debug(s"Requesting status of job $cortexJobId in cortex ${cortex.name} in order to update job $jobId")
    cortex.waitReport(cortexJobId, 1.minute) andThen {
      case Success(j) ⇒
        val status = (j \ "status").asOpt[JobStatus.Type].getOrElse(JobStatus.Failure)
        if (status == JobStatus.InProgress || status == JobStatus.Waiting)
          updateJobWithCortex(jobId, cortexJobId, cortex)
        else {
          val report = (j \ "report").asOpt[JsObject].getOrElse(JsObject.empty).toString
          logger.debug(s"Job $cortexJobId in cortex ${cortex.name} has finished with status $status, updating job $jobId")
          getSrv[JobModel, Job](jobModel, jobId)
            .flatMap { job ⇒
              val jobFields = Fields.empty
                .set("status", status.toString)
                .set("report", report)
                .set("endDate", Json.toJson(new Date))
              update(job, jobFields)
                .andThen {
                  case _ if status == JobStatus.Success ⇒
                    val jobSummary = Try(Json.parse(report))
                      .toOption
                      .flatMap(r ⇒ (r \ "summary").asOpt[JsObject])
                      .getOrElse(JsObject.empty)
                    retryIf(_ ⇒ true, 5) {
                      for {
                        artifact ← artifactSrv.get(job.artifactId())
                        reports = Try(Json.parse(artifact.reports()).asOpt[JsObject]).toOption.flatten.getOrElse(JsObject.empty)
                        newReports = reports + (job.analyzerDefinition().getOrElse(job.analyzerId()) → jobSummary)
                      } yield artifactSrv.update(job.artifactId(), Fields.empty.set("reports", newReports.toString), ModifyConfig(retryOnConflict = 0, version = Some(artifact.version)))
                    }
                      .recover {
                        case NonFatal(t) ⇒ logger.warn(s"Unable to insert summary report in artifact", t)
                      }

                }
            }
            .onComplete {
              case Failure(e) ⇒ logger.error(s"Update job fails", e)
              case _          ⇒
            }
        }
      case Failure(CortexError(404, _, _)) ⇒
        logger.debug(s"The job $cortexJobId not found")
        val jobFields = Fields.empty
          .set("status", JobStatus.Failure.toString)
          .set("endDate", Json.toJson(new Date))
        update(jobId, jobFields)
      case _ ⇒
        logger.debug(s"Request of status of job $cortexJobId in cortex ${cortex.name} fails, restarting ...")
        updateJobWithCortex(jobId, cortexJobId, cortex)
    }
    ()
  }

  def submitJob(cortexId: Option[String], analyzerName: String, artifactId: String)(implicit authContext: AuthContext): Future[Job] = {
    val cortexClientAnalyzer = cortexId match {
      case Some(id) ⇒
        cortexConfig
          .instances
          .find(_.name == id)
          .fold[Future[(CortexClient, Analyzer)]](Future.failed(NotFoundError(s"cortex $id not found"))) { c ⇒
            c.getAnalyzer(analyzerName)
              .map(c -> _)
          }

      case None ⇒
        Future.firstCompletedOf {
          cortexConfig.instances.map(c ⇒ c.getAnalyzer(analyzerName).map(c -> _))
        }
    }

    cortexClientAnalyzer.flatMap {
      case (cortex, analyzer) ⇒
        for {
          artifact ← artifactSrv.get(artifactId)
          artifactAttributes = Json.obj(
            "tlp" → artifact.tlp(),
            "dataType" → artifact.dataType())
          cortexArtifact = (artifact.data(), artifact.attachment()) match {
            case (Some(data), None)       ⇒ DataArtifact(data, artifactAttributes)
            case (None, Some(attachment)) ⇒ FileArtifact(attachmentSrv.source(attachment.id), artifactAttributes + ("attachment" → Json.toJson(attachment)))
            case _                        ⇒ throw InternalError(s"Artifact has invalid data : ${artifact.attributes}")
          }
          cortexJobJson ← cortex.analyze(analyzer.id, cortexArtifact)
          cortexJob = cortexJobJson.as[CortexJob]
          job ← create(artifact, Fields.empty
            .set("analyzerId", cortexJob.analyzerId)
            .set("analyzerName", cortexJob.analyzerName)
            .set("analyzerDefinition", cortexJob.analyzerDefinition)
            .set("artifactId", artifactId)
            .set("cortexId", cortex.name)
            .set("cortexJobId", cortexJob.id))
          _ = updateJobWithCortex(job.id, cortexJob.id, cortex)
        } yield job
    }
  }
}
