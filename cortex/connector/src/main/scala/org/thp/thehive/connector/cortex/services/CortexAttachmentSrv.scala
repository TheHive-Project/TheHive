package org.thp.thehive.connector.cortex.services

import java.nio.file.Files

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import javax.inject.{Inject, Singleton}
import org.thp.cortex.client.CortexClient
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models.Attachment
import org.thp.thehive.services.AttachmentSrv

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CortexAttachmentSrv @Inject()(attachmentSrv: AttachmentSrv, implicit val ex: ExecutionContext, implicit val mat: Materializer, db: Database) {

  /**
    * For a CortexOutputJob, downloads the attachment file for each artifact from the job's report,
    * if it is a file artifact obviously and saves an Attachment is the db
    *
    * @param jobId the internal job id
    * @param job the job coming from Cortex
    * @param cortexClient the client api
    * @param authContext the auth context necessary for db persistence
    * @return
    */
  def downloadAttachments(jobId: String, job: CortexOutputJob, cortexClient: CortexClient)(
      implicit authContext: AuthContext
  ): Future[Seq[Attachment with Entity]] =
    Future.sequence(for {
      report   <- job.report.toSeq
      artifact <- report.artifacts
      if artifact.dataType == "file"
      attachment <- artifact.attachment
    } yield {
      val file = Files.createTempFile(s"job-$jobId-cortex-${job.id}-${attachment.id}", "")
      for {
        src <- cortexClient.getAttachment(attachment.id)
        s   <- src.runWith(FileIO.toPath(file))
        _   <- Future.fromTry(s.status)
        fFile = FFile(attachment.name.getOrElse(attachment.id), file, attachment.contentType.getOrElse("application/octet-stream"))
        savedAttachment <- db.transaction(implicit graph => Future.fromTry(attachmentSrv.create(fFile)))
        _ = Files.delete(file)
      } yield savedAttachment
    })
}
