package org.thp.thehive.connector.cortex.services

import java.nio.file.Files

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.CortexOutputArtifact
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.connector.cortex.models.Job
import org.thp.thehive.models._
import org.thp.thehive.services.{AttachmentSrv, DataSrv, ObservableSrv}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ArtifactSrv @Inject()(
    attachmentSrv: AttachmentSrv,
    observableSrv: ObservableSrv,
    dataSrv: DataSrv,
    implicit val ex: ExecutionContext,
    implicit val mat: Materializer,
    db: Database
) {

  type AttachmentOrData = Either[Future[Attachment with Entity], Try[Data with Entity]]

  /**
    * Processes an artifact coming from Cortex job report
    * if it is a file: downloads the attachment file and creates a db Attachment
    * else creates a db Data
    *
    * @param artifact the artifact to process
    * @param job the related db job
    * @param observable the related db observable
    * @param cortexClient client
    * @param authContext authContext for db queries
    * @param graph graph connection
    * @return
    */
  def process(
      artifact: CortexOutputArtifact,
      job: Job with Entity,
      observable: Observable with Entity,
      cortexClient: CortexClient
  )(implicit authContext: AuthContext, graph: Graph): AttachmentOrData = artifact.dataType match {
    case "file" =>
      Left(for {
        attachment <- downloadAttachment(job._id, job.cortexJobId, artifact, cortexClient)
        _ <- Future.fromTry(
          Try(observableSrv.observableAttachmentSrv.create(ObservableAttachment(), observable, attachment))
        )
      } yield attachment)

    case _ =>
      Right(for {
        dataStr <- Try(artifact.data.get)
        data    <- Try(dataSrv.create(Data(dataStr)))
        _       <- Try(observableSrv.observableDataSrv.create(ObservableData(), observable, data))
      } yield data)
  }

  /**
    * Downloads the attachment file for an artifact of type file
    * from the job's report and saves the Attachment to the db
    *
    * @param jobId the internal job id
    * @param cortexClient the client api
    * @param authContext the auth context necessary for db persistence
    * @return
    */
  def downloadAttachment(jobId: String, cortexJobId: String, artifact: CortexOutputArtifact, cortexClient: CortexClient)(
      implicit authContext: AuthContext
  ): Future[Attachment with Entity] = {
    for {
      attachment <- artifact.attachment
    } yield {
      val file = Files.createTempFile(s"job-$jobId-cortex-$cortexJobId-${attachment.id}", "")
      for {
        src <- cortexClient.getAttachment(attachment.id)
        s   <- src.runWith(FileIO.toPath(file))
        _   <- Future.fromTry(s.status)
        fFile = FFile(attachment.name.getOrElse(attachment.id), file, attachment.contentType.getOrElse("application/octet-stream"))
        savedAttachment <- db.transaction(implicit graph => Future.fromTry(attachmentSrv.create(fFile)))
        _ = Files.delete(file)
      } yield savedAttachment
    }
  } getOrElse Future.failed(new Exception(s"Attachment not present for artifact ${artifact.dataType}"))
}
