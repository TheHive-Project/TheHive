package org.thp.thehive.connector.cortex.services

import akka.stream.scaladsl.StreamConverters
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.{CortexClient, CortexConfig}
import org.thp.cortex.dto.v0.{InputCortexArtifact, Attachment ⇒ CortexAttachment}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.{EntitySteps, NotFoundError}
import org.thp.thehive.connector.cortex.controllers.v0.JobConversion
import org.thp.thehive.connector.cortex.models.{Job, ObservableJob}
import org.thp.thehive.models._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JobSrv @Inject()(
    implicit db: Database,
    cortexConfig: CortexConfig,
    storageSrv: StorageSrv,
    implicit val ex: ExecutionContext
) extends VertexSrv[Job, JobSteps]
    with JobConversion {

  val observableJobSrv = new EdgeSrv[ObservableJob, Observable, Job]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): JobSteps = new JobSteps(raw)

  /**
    * Submits an observable for analyzis to cortex client and stores
    * resulting job
    *
    * @param cortexId the client id name
    * @param workerId the analyzer (worker) id
    * @param observable the observable to analyze
    * @param `case` the related case
    * @param graph graph db instance
    * @param authContext auth context instance
    * @return
    */
  def submitJob(cortexId: String, workerId: String, observable: RichObservable, `case`: Case with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Future[Job with Entity] =
    for {
      cortexClient ← cortexConfig
        .instances
        .find(_.name == cortexId)
        .fold[Future[CortexClient]](Future.failed(NotFoundError(s"Cortex $cortexId not found")))(Future.successful)
      analyzer ← cortexClient.getAnalyzer(workerId).recoverWith { case _ ⇒ cortexClient.getAnalyzerByName(workerId) } // if get analyzer using cortex2 API fails, try using legacy API
      cortexArtifact ← (observable.attachment, observable.data) match {
        case (None, Some(data)) ⇒
          Future.successful(InputCortexArtifact(observable.tlp, `case`.pap, observable.`type`, `case`._id, Some(data.data), None))
        case (Some(a), None) ⇒
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
                  StreamConverters.fromInputStream(() ⇒ storageSrv.loadBinary(a.hashes.head.toString))
                )
              )
            )
          )
        case _ ⇒ Future.failed(new Exception(s"Invalid Observable data for ${observable.observable._id}"))
      }
      cortexOutputJob ← cortexClient.analyse(analyzer.id, cortexArtifact)
      createdJob = db.transaction { implicit newGraph ⇒
        create(fromCortexOutputJob(cortexOutputJob).copy(cortexId = cortexId), observable.observable)(newGraph, authContext)
      }
    } yield createdJob

  /**
    * Creates a Job with with according ObservableJob edge
    *
    * @param job the job date to create
    * @param observable the related observable
    * @param graph the implicit graph instance needed
    * @param authContext the implicit auth needed
    * @return
    */
  def create(job: Job, observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Job with Entity = {
    val createdJob = create(job)
    observableJobSrv.create(ObservableJob(), observable, createdJob)

    createdJob
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
}
