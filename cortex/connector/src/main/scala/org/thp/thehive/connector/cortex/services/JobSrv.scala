package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.actor._
import akka.stream.scaladsl.StreamConverters
import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.{CortexOutputJob, InputCortexArtifact, Attachment => CortexAttachment}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, NotFoundError}
import org.thp.thehive.connector.cortex.controllers.v0.{ArtifactConversion, JobConversion}
import org.thp.thehive.connector.cortex.models.{Job, ObservableJob, ReportObservable}
import org.thp.thehive.connector.cortex.services.CortexActor.CheckJob
import org.thp.thehive.models._
import org.thp.thehive.services.{ObservableSrv, ObservableSteps}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class JobSrv @Inject()(
    implicit db: Database,
    cortexConfig: CortexConfig,
    storageSrv: StorageSrv,
    implicit val ex: ExecutionContext,
    @Named("cortex-actor") cortexActor: ActorRef,
    cortexAttachmentSrv: ArtifactSrv,
    observableSrv: ObservableSrv,
    artifactSrv: ArtifactSrv
) extends VertexSrv[Job, JobSteps] {

  import ArtifactConversion._
  import JobConversion._

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
      cortexClient <- cortexConfig
        .instances
        .get(cortexId)
        .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
      analyzer <- cortexClient.getAnalyzer(workerId).recoverWith { case _ => cortexClient.getAnalyzerByName(workerId) } // if get analyzer using cortex2 API fails, try using legacy API
      cortexArtifact <- (observable.attachment, observable.data) match {
        case (None, Some(data)) =>
          Future.successful(InputCortexArtifact(observable.tlp, `case`.pap, observable.`type`, `case`._id, Some(data.data), None))
        case (Some(a), None) =>
          val data = StreamConverters.fromInputStream(() => storageSrv.loadBinary(a.attachmentId))
          Future.successful(
            InputCortexArtifact(
              observable.tlp,
              `case`.pap,
              observable.`type`,
              `case`._id,
              None,
              Some(
                CortexAttachment(
                  a.name,
                  a.size,
                  a.contentType,
                  data
                )
              )
            )
          )
        case _ => Future.failed(new Exception(s"Invalid Observable data for ${observable.observable._id}"))
      }
      cortexOutputJob <- cortexClient.analyse(analyzer.id, cortexArtifact)
      createdJob = db.transaction { implicit graph =>
        create(fromCortexOutputJob(cortexOutputJob).copy(cortexId = cortexId), observable.observable)
      }
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
  def create(job: Job, observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Job with Entity = {
    val createdJob = create(job)
    observableJobSrv.create(ObservableJob(), observable, createdJob)

    createdJob
  }

  /**
    * Once a job has finished on Cortex side
    * the report is processed here: each report's artifacts
    * are stored as separate Observable with the appropriate edge ObservableJob
    *
    * @param jobId        the job db id
    * @param cortexJob    the CortexOutputJob
    * @param cortexClient client for Cortex api
    * @param authContext  the auth context for db queries
    * @return
    */
  def finished(jobId: String, cortexJob: CortexOutputJob, cortexClient: CortexClient)(
      implicit authContext: AuthContext
  ): Try[Job with Entity] =
    db.transaction { implicit graph =>
      cortexJob.report.foreach { report =>
        for {
          artifact <- report.artifacts
          obs = observableSrv.create(artifact)
          job <- get(jobId).headOption()
          _ = reportObservableSrv.create(ReportObservable(), job, obs)
          _ = artifactSrv.process(artifact, job, obs, cortexClient)
        } ()
      }
      get(jobId).update(
        "report"  -> cortexJob.report.map(r => Json.toJson(r).as[JsObject] - "artifacts"),
        "status"  -> fromCortexJobStatus(cortexJob.status),
        "endDate" -> new Date()
      )
    }
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
