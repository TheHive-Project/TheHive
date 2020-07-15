package org.thp.thehive.connector.cortex.services

import java.nio.file.Files
import java.util.Date

import akka.Done
import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.google.inject.name.Named
import gremlin.scala._
import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.{InputArtifact, OutputArtifact, Attachment => CortexAttachment, OutputJob => CortexJob}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, TraversalLike, VertexSteps}
import org.thp.scalligraph.{EntitySteps, NotFoundError}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models._
import org.thp.thehive.connector.cortex.services.Conversion._
import org.thp.thehive.connector.cortex.services.CortexActor.CheckJob
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.{AttachmentSrv, ObservableSrv, ObservableSteps, ObservableTypeSrv, ReportTagSrv}
import play.api.libs.json.Json

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class JobSrv @Inject() (
    connector: Connector,
    @Named("cortex-actor") cortexActor: ActorRef,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    reportTagSrv: ReportTagSrv,
    serviceHelper: ServiceHelper,
    auditSrv: CortexAuditSrv,
    @Named("with-thehive-schema") implicit val db: Database,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends VertexSrv[Job, JobSteps] {

  val observableJobSrv    = new EdgeSrv[ObservableJob, Observable, Job]
  val reportObservableSrv = new EdgeSrv[ReportObservable, Job, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): JobSteps = new JobSteps(raw)

  /**
    * Submits an observable for analysis to cortex client and stores
    * resulting job and send the cortex reference id to the polling job status actor
    *
    * @param cortexId    the client id name
    * @param workerId    the analyzer (worker) id
    * @param observable  the observable to analyze
    * @param `case`      the related case
    * @param authContext auth context instance
    * @return
    */
  def submit(cortexId: String, workerId: String, observable: RichObservable, `case`: Case with Entity)(
      implicit authContext: AuthContext
  ): Future[RichJob] =
    for {
      cortexClient <- serviceHelper
        .availableCortexClients(connector.clients, authContext.organisation)
        .find(_.name == cortexId)
        .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
      analyzer <- cortexClient.getAnalyzer(workerId).recoverWith { case _ => cortexClient.getAnalyzerByName(workerId) } // if get analyzer using cortex2 API fails, try using legacy API
      cortexArtifact <- (observable.attachment, observable.data) match {
        case (None, Some(data)) =>
          Future.successful(
            InputArtifact(observable.tlp, `case`.pap, observable.`type`.name, `case`._id, Some(data.data), None)
          )
        case (Some(a), None) =>
          val attachment = CortexAttachment(a.name, a.size, a.contentType, attachmentSrv.source(a))
          Future.successful(
            InputArtifact(observable.tlp, `case`.pap, observable.`type`.name, `case`._id, None, Some(attachment))
          )
        case _ => Future.failed(new Exception(s"Invalid Observable data for ${observable.observable._id}"))
      }
      cortexOutputJob <- cortexClient.analyse(analyzer.id, cortexArtifact)
      createdJob <- Future.fromTry(db.tryTransaction { implicit graph =>
        create(fromCortexOutputJob(cortexOutputJob).copy(cortexId = cortexId), observable.observable)
      })
      _ <- Future.fromTry(db.tryTransaction { implicit graph =>
        auditSrv.job.create(createdJob.job, observable.observable, createdJob.toJson)
      })
      _ = cortexActor ! CheckJob(Some(createdJob._id), cortexOutputJob.id, None, cortexClient.name, authContext)
    } yield createdJob

  private def fromCortexOutputJob(j: CortexJob): Job =
    j.into[Job]
      .withFieldComputed(_.workerId, _.workerId)
      .withFieldComputed(_.workerName, _.workerName)
      .withFieldComputed(_.workerDefinition, _.workerDefinition)
      .withFieldComputed(_.status, s => JobStatus.withName(s.status.toString))
      .withFieldComputed(_.startDate, j => j.startDate.getOrElse(j.date))
      .withFieldComputed(_.endDate, j => j.endDate.getOrElse(j.date))
      .withFieldConst(_.report, None)
      .withFieldConst(_.cortexId, "tbd")
      .withFieldComputed(_.cortexJobId, _.id)
      .transform

  /**
    * Creates a Job with with according ObservableJob edge
    *
    * @param job         the job date to create
    * @param observable  the related observable
    * @param graph       the implicit graph instance needed
    * @param authContext the implicit auth needed
    * @return
    */
  def create(job: Job, observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[RichJob] =
    for {
      createdJob <- createEntity(job)
      _          <- observableJobSrv.create(ObservableJob(), observable, createdJob)
    } yield RichJob(createdJob, Nil)

  /**
    * Once a job has finished on Cortex side
    * the report is processed here: each report's artifacts
    * are stored as separate Observable with the appropriate edge ObservableJob
    *
    * @param cortexId     Id of cortex
    * @param jobId        the job db id
    * @param cortexJob    the CortexOutputJob
    * @param authContext  the auth context for db queries
    * @return the updated job
    */
  def finished(cortexId: String, jobId: String, cortexJob: CortexJob)(
      implicit authContext: AuthContext
  ): Future[Job with Entity] =
    for {
      cortexClient <- serviceHelper
        .availableCortexClients(connector.clients, authContext.organisation)
        .find(_.name == cortexId)
        .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
      job <- Future.fromTry(updateJobStatus(jobId, cortexJob))
      _   <- importCortexArtifacts(job, cortexJob, cortexClient)
      _   <- Future.fromTry(importAnalyzerTags(job, cortexJob))
    } yield job

  /**
    * Update job status, set the endDate and remove artifacts from report
    *
    * @param jobId id of the job to update
    * @param cortexJob the job from cortex
    * @param authContext the authentication context
    * @return the updated job
    */
  private def updateJobStatus(jobId: String, cortexJob: CortexJob)(implicit authContext: AuthContext): Try[Job with Entity] =
    db.tryTransaction { implicit graph =>
      getOrFail(jobId).flatMap { job =>
        val report  = cortexJob.report.flatMap(r => r.full orElse r.errorMessage.map(m => Json.obj("errorMessage" -> m)))
        val status  = cortexJob.status.toJobStatus
        val endDate = new Date()
        for {
          job <- get(job).updateOne(
            "report"  -> report,
            "status"  -> status,
            "endDate" -> endDate
          )
          observable <- get(job).observable.getOrFail()
          _          <- auditSrv.job.update(job, observable, Json.obj("status" -> status, "endDate" -> endDate))
        } yield job
      }
    }

  private def importAnalyzerTags(job: Job with Entity, cortexJob: CortexJob)(implicit authContext: AuthContext): Try[Unit] =
    db.tryTransaction { implicit graph =>
      val tags = cortexJob.report.fold[Seq[ReportTag]](Nil)(_.summary.map(_.toAnalyzerTag(job.workerName)))
      for {
        observable <- get(job).observable.getOrFail()
        _          <- reportTagSrv.updateTags(observable, job.workerName, tags)
      } yield ()
    }

  /**
    * Create observable for each artifact of the job report
    *
    * @param job the job on which the observables will be linked
    * @param cortexJob the cortex job containing the artifact to import
    * @param cortexClient the cortex client used to download attachment observable
    * @param authContext the authentication context
    * @return
    */
  private def importCortexArtifacts(job: Job with Entity, cortexJob: CortexJob, cortexClient: CortexClient)(
      implicit authContext: AuthContext
  ): Future[Done] = {
    val artifacts = cortexJob
      .report
      .toList
      .flatMap(_.artifacts)
    Future
      .traverse(artifacts) { artifact =>
        db.tryTransaction(graph => observableTypeSrv.getOrFail(artifact.dataType)(graph)) match {
          case Success(attachmentType) if attachmentType.isAttachment => importCortexAttachment(job, artifact, attachmentType, cortexClient)
          case Success(dataType) =>
            Future
              .fromTry {
                db.tryTransaction { implicit graph =>
                  observableSrv
                    .create(artifact.toObservable, dataType, artifact.data.get, artifact.tags, Nil)
                    .flatMap { richObservable =>
                      addObservable(job, richObservable.observable)
                    }
                }
              }
          case Failure(e) => Future.failed(e)
        }
      }
      .map(_ => Done)
  }

  def addObservable(
      job: Job with Entity,
      observable: Observable with Entity
  )(implicit graph: Graph, authContext: AuthContext): Try[ReportObservable with Entity] =
    reportObservableSrv.create(ReportObservable(), job, observable)

  /**
    * Downloads and import the attachment file for an artifact of type file
    * from the job's report and saves the Attachment to the db
    *
    * @param cortexClient the client api
    * @param authContext  the auth context necessary for db persistence
    * @return
    */
  private def importCortexAttachment(
      job: Job with Entity,
      artifact: OutputArtifact,
      attachmentType: ObservableType with Entity,
      cortexClient: CortexClient
  )(
      implicit authContext: AuthContext
  ): Future[Attachment with Entity] =
    artifact
      .attachment
      .map { attachment =>
        val file = Files.createTempFile(s"job-cortex-${attachment.id}", "")
        (for {
          src <- cortexClient.getAttachment(attachment.id)
          _   <- src.runWith(FileIO.toPath(file))
          fFile = FFile(attachment.name.getOrElse(attachment.id), file, attachment.contentType.getOrElse("application/octet-stream"))
          savedAttachment <- Future.fromTry {
            db.tryTransaction { implicit graph =>
              for {
                createdAttachment <- attachmentSrv.create(fFile)
                richObservable    <- observableSrv.create(artifact.toObservable, attachmentType, createdAttachment, artifact.tags, Nil)
                _                 <- reportObservableSrv.create(ReportObservable(), job, richObservable.observable)
              } yield createdAttachment
            }
          }
        } yield savedAttachment)
          .andThen { case _ => Files.delete(file) }
      }
      .getOrElse(Future.failed(new Exception(s"Attachment not present for artifact ${artifact.dataType}")))

}

@EntitySteps[Job]
class JobSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Job](raw) {

  /**
    * Checks if a Job is visible from a certain UserRole end
    *
    * @param authContext the auth context to check login against
    * @return
    */
  def visible(implicit authContext: AuthContext): JobSteps =
    this.filter(
      _.inTo[ObservableJob]
        .inTo[ShareObservable]
        .inTo[OrganisationShare]
        .has("name", authContext.organisation)
    )

  /**
    * Checks if a job is accessible if the user and
    * the share profile contain the permission
    * @param permission the permission to check
    * @param authContext the user context
    * @return
    */
  def can(permission: Permission)(implicit authContext: AuthContext): JobSteps =
    if (authContext.permissions.contains(permission))
      this.filter(
        _.inTo[ObservableJob]
          .inTo[ShareObservable]
          .filter(_.outTo[ShareProfile].has("permissions", permission))
          .inTo[OrganisationShare]
          .has("name", authContext.organisation)
      )
    else this.limit(0)

  override def newInstance(newRaw: GremlinScala[Vertex]): JobSteps = new JobSteps(newRaw)
  override def newInstance(): JobSteps                             = new JobSteps(raw.clone())

  def observable: ObservableSteps = new ObservableSteps(raw.inTo[ObservableJob])

  /**
    * Returns the potential observables that were attached to a job report
    * after analyze has completed
    *
    * @return
    */
  def reportObservables: ObservableSteps = new ObservableSteps(raw.outTo[ReportObservable])

  def richJob(implicit authContext: AuthContext): Traversal[RichJob, RichJob] = {
    val thisJob = StepLabel()
    this
      .as(thisJob)
      .project(
        _.by
          .by(
            _.reportObservables
              .project(
                _.by(_.richObservable)
                  .by(_.similar.filter(_.`case`.observables.outTo[ObservableJob].where(P.eq[String](thisJob.name)))._id.fold)
              )
              .fold
          )
      )
      .map {
        case (job, observablesWithLink) =>
          val observables = observablesWithLink.asScala.map {
            case (obs, l) => obs -> Json.obj("observableId" -> l.asScala.headOption.map(_.toString))
          }
          RichJob(job.as[Job], observables)
      }
  }
  def richJobWithCustomRenderer[A](
      entityRenderer: JobSteps => TraversalLike[_, A]
  )(implicit authContext: AuthContext): Traversal[(RichJob, A), (RichJob, A)] = {
    val thisJob = StepLabel()
    this
      .as(thisJob)
      .project(
        _.by
          .by(
            _.reportObservables
              .project(
                _.by(_.richObservable)
                  .by(_.similar.filter(_.`case`.observables.outTo[ObservableJob].where(P.eq[String](thisJob.name)))._id.fold)
              )
              .fold
          )
          .by(entityRenderer)
      )
      .map {
        case (job, observablesWithLink, renderedEntity) =>
          val observables = observablesWithLink.asScala.map {
            case (obs, l) => obs -> Json.obj("observableId" -> l.asScala.headOption.map(_.toString))
          }
          RichJob(job.as[Job], observables) -> renderedEntity
      }
  }
}
