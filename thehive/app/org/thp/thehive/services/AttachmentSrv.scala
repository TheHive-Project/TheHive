package org.thp.thehive.services

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.services.{StorageSrv, VertexSrv}
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.models.Attachment
import play.api.Configuration

import java.io.InputStream
import java.nio.file.Files
import scala.concurrent.Future
import scala.util.Try

class AttachmentSrv(configuration: Configuration, storageSrv: StorageSrv, mat: Materializer) extends VertexSrv[Attachment] with TheHiveOpsNoDeps {
  implicit val _mat: Materializer = mat

  val hashers: Hasher = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    val hs = hashers.fromPath(file.filepath)
    val id = hs.head.toString
    val is = Files.newInputStream(file.filepath)
    try storageSrv
      .saveBinary("attachment", id, is)
      .flatMap(_ => createEntity(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs, id)))
    finally is.close()
  }

  def create(filename: String, contentType: String, data: Array[Byte])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary("attachment", id, data).flatMap(_ => createEntity(Attachment(filename, data.length.toLong, contentType, hs, id)))
  }

  def create(filename: String, size: Long, contentType: String, data: Source[ByteString, NotUsed])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary("attachment", id, data).flatMap(_ => createEntity(Attachment(filename, size, contentType, hs, id)))
  }

  def duplicate(filename: String, contentType: String, attachmentId: String)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val (size, hashes) = getByName(attachmentId).headOption match {
      case Some(a) => (a.size, a.hashes)
      case None =>
        val s  = storageSrv.getSize("attachment", attachmentId).getOrElse(throw NotFoundError(s"Attachment $attachmentId not found"))
        val hs = hashers.fromInputStream(storageSrv.loadBinary("attachment", attachmentId))
        (s, hs)
    }
    createEntity(Attachment(filename, size, contentType, hashes, attachmentId))
  }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Attachment] =
    startTraversal.getByAttachmentId(name)

  def source(attachment: Attachment with Entity): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity): InputStream = storageSrv.loadBinary("attachment", attachment.attachmentId)

  def exists(attachment: Attachment with Entity): Boolean = storageSrv.exists("attachment", attachment.attachmentId)

  override def delete(attachment: Attachment with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val attachments = startTraversal.has(_.attachmentId, attachment.attachmentId).limit(2).getCount
    if (attachments == 1)
      storageSrv.delete("attachment", attachment.attachmentId)

    Try(get(attachment).remove())
  }

}

trait AttachmentOps { _: TheHiveOpsNoDeps =>
  implicit class AttachmentOpsDefs(traversal: Traversal.V[Attachment]) {
    def getByAttachmentId(attachmentId: String): Traversal.V[Attachment] = traversal.has(_.attachmentId, attachmentId)

    def visible: Traversal.V[Attachment] = traversal // TODO

  }
}
