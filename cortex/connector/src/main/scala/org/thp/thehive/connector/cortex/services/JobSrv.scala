package org.thp.thehive.connector.cortex.services

import java.nio.file.Files
import java.util.Date

import akka.Done
import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, StreamConverters}
import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.{CortexOutputArtifact, CortexOutputJob, InputCortexArtifact, Attachment => CortexAttachment}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, NotFoundError}
import org.thp.thehive.connector.cortex.controllers.v0.{ArtifactConversion, JobConversion}
import org.thp.thehive.connector.cortex.models.{Job, ObservableJob, ReportObservable}
import org.thp.thehive.connector.cortex.services.CortexActor.CheckJob
import org.thp.thehive.models._
import org.thp.thehive.services.{AttachmentSrv, ObservableSrv, ObservableSteps, ObservableTypeSrv}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class JobSrv @Inject()(
    implicit db: Database,
    cortexConfig: CortexConfig,
    storageSrv: StorageSrv,
    @Named("cortex-actor") cortexActor: ActorRef,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer,
    serviceHelper: ServiceHelper
) extends VertexSrv[Job, JobSteps] {

  import ArtifactConversion._
  import JobConversion._

  lazy val logger         = Logger(getClass)
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
  ): Future[Job with Entity] =
    for {
      cortexClient <- serviceHelper
        .availableCortexClients(cortexConfig, Organisation(authContext.organisation))
        .find(_.name == cortexId)
        .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
      analyzer <- cortexClient.getAnalyzer(workerId).recoverWith { case _ => cortexClient.getAnalyzerByName(workerId) } // if get analyzer using cortex2 API fails, try using legacy API
      cortexArtifact <- (observable.attachment, observable.data) match {
        case (None, Some(data)) =>
          Future.successful(
            InputCortexArtifact(observable.tlp, `case`.pap, observable.`type`.name, `case`._id, Some(data.data), None)
          )
        case (Some(a), None) =>
          val data       = StreamConverters.fromInputStream(() => storageSrv.loadBinary(a.attachmentId))
          val attachment = CortexAttachment(a.name, a.size, a.contentType, data)
          Future.successful(
            InputCortexArtifact(observable.tlp, `case`.pap, observable.`type`.name, `case`._id, None, Some(attachment))
          )
        case _ => Future.failed(new Exception(s"Invalid Observable data for ${observable.observable._id}"))
      }
      cortexOutputJob <- cortexClient.analyse(analyzer.id, cortexArtifact)
      createdJob <- Future.fromTry(db.tryTransaction { implicit graph =>
        create(fromCortexOutputJob(cortexOutputJob).copy(cortexId = cortexId), observable.observable)
      })
      _ = cortexActor ! CheckJob(Some(createdJob._id), cortexOutputJob.id, None, cortexClient, authContext)
    } yield createdJob

  /**
    * Creates a Job with with according ObservableJob edge
    *
    * @param job         the job date to create
    * @param observable  the related observable
    * @param graph       the implicit graph instance needed
    * @param authContext the implicit auth needed
    * @return
    */
  def create(job: Job, observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Job with Entity] =
    for {
      createdJob <- create(job)
      _          <- observableJobSrv.create(ObservableJob(), observable, createdJob)
    } yield createdJob

  /**
    * Once a job has finished on Cortex side
    * the report is processed here: each report's artifacts
    * are stored as separate Observable with the appropriate edge ObservableJob
    *
    * @param jobId        the job db id
    * @param cortexJob    the CortexOutputJob
    * @param cortexClient client for Cortex api
    * @param authContext  the auth context for db queries
    * @return the updated job
    */
  def finished(jobId: String, cortexJob: CortexOutputJob, cortexClient: CortexClient)(
      implicit authContext: AuthContext
  ): Future[Job with Entity] =
    for {
      job <- Future.fromTry(updateJobStatus(jobId, cortexJob))
      _   <- importCortexArtifacts(job, cortexJob, cortexClient)
    } yield job

  /**
    * Update job status, set the endDate and remove artifacts from report
    *
    * @param jobId id of the job to update
    * @param cortexJob the job from cortex
    * @param authContext the authentication context
    * @return the updated job
    */
  def updateJobStatus(jobId: String, cortexJob: CortexOutputJob)(implicit authContext: AuthContext): Try[Job with Entity] =
    db.tryTransaction { implicit graph =>
      getOrFail(jobId).flatMap { job =>
        get(job).update(
          "report"  -> cortexJob.report.map(r => Json.toJson(r).as[JsObject] - "artifacts"),
          "status"  -> fromCortexJobStatus(cortexJob.status),
          "endDate" -> new Date()
        )
      }
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
  def importCortexArtifacts(job: Job with Entity, cortexJob: CortexOutputJob, cortexClient: CortexClient)(
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
                    .create(artifact, dataType, artifact.data.get, artifact.tags, Nil)
                    .map { richObservable =>
                      reportObservableSrv.create(ReportObservable(), job, richObservable.observable)
                    }
                }
              }
          case Failure(e) => Future.failed(e)
        }
      }
      .map(_ => Done)
  }

  /**
    * Downloads and import the attachment file for an artifact of type file
    * from the job's report and saves the Attachment to the db
    *
    * @param cortexClient the client api
    * @param authContext  the auth context necessary for db persistence
    * @return
    */
  def importCortexAttachment(
      job: Job with Entity,
      artifact: CortexOutputArtifact,
      attachmentType: ObservableType with Entity,
      cortexClient: CortexClient
  )(
      implicit authContext: AuthContext
  ): Future[Attachment with Entity] =
    artifact
      .attachment
      .map { attachment =>
        val file = Files.createTempFile(s"job-cortex-${attachment.id}", "")
        val attach = for {
          src <- cortexClient.getAttachment(attachment.id)
          s   <- src.runWith(FileIO.toPath(file))
          _   <- Future.fromTry(s.status)
          fFile = FFile(attachment.name.getOrElse(attachment.id), file, attachment.contentType.getOrElse("application/octet-stream"))
          savedAttachment <- Future.fromTry {
            db.tryTransaction { implicit graph =>
              for {
                createdAttachment <- attachmentSrv.create(fFile)
                richObservable    <- observableSrv.create(artifact, attachmentType, createdAttachment, artifact.tags, Nil)
                _ = reportObservableSrv.create(ReportObservable(), job, richObservable.observable)
              } yield createdAttachment
            }
          }
          _ = Files.delete(file)
        } yield savedAttachment

        attach transform {
          case s @ Success(_) => s
          case Failure(cause) =>
            Files.delete(file)
            Failure(new Exception("Saving attachment failed", cause))
        }
      }
      .getOrElse(Future.failed(new Exception(s"Attachment not present for artifact ${artifact.dataType}")))

}

@EntitySteps[Job]
class JobSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Job, JobSteps](raw) {

  /**
    * Checks if a Job is visible from a certain UserRole end
    *
    * @param authContext the auth context to check login against
    * @return
    */
  def visible(implicit authContext: AuthContext): JobSteps = newInstance(
    raw.filter(
      _.inTo[ObservableJob]
        .inTo[ShareObservable]
        .inTo[OrganisationShare]
        .inTo[RoleOrganisation]
        .inTo[UserRole]
        .has(Key("login") of authContext.userId)
    )
  )

  override def newInstance(raw: GremlinScala[Vertex]): JobSteps = new JobSteps(raw)

  def observable: ObservableSteps = new ObservableSteps(raw.inTo[ObservableJob])

  /**
    * Returns the potential observables that were attached to a job report
    * after analyze has completed
    *
    * @return
    */
  def reportObservables: ObservableSteps = new ObservableSteps(raw.outTo[ReportObservable])
}
