package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.Files

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{StorageSrv, VertexSrv}
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.models.Attachment
import org.thp.thehive.services.AttachmentOps._
import play.api.Configuration

import scala.concurrent.Future
import scala.util.Try

@Singleton
class AttachmentSrv @Inject() (configuration: Configuration, storageSrv: StorageSrv)(implicit
    @Named("with-thehive-schema") db: Database,
    mat: Materializer
) extends VertexSrv[Attachment] {

  val hashers: Hasher = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    val hs = hashers.fromPath(file.filepath)
    val id = hs.head.toString
    val is = Files.newInputStream(file.filepath)
    val result =
      storageSrv
        .saveBinary("attachment", id, is)
        .flatMap(_ => createEntity(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs, id)))
    is.close()
    result
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

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Attachment] =
    startTraversal.getByAttachmentId(name)

  def source(attachment: Attachment with Entity): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity): InputStream = storageSrv.loadBinary("attachment", attachment.attachmentId)

  def exists(attachment: Attachment with Entity): Boolean = storageSrv.exists("attachment", attachment.attachmentId)

  def cascadeRemove(attachment: Attachment with Entity)(implicit graph: Graph): Unit = {
    val attachments = startTraversal.has(_.attachmentId, attachment.attachmentId).limit(2).getCount
    if (attachments == 1) {
      storageSrv.delete("attachment", attachment.attachmentId)
    }

    get(attachment).remove()
  }

}

object AttachmentOps {
  implicit class AttachmentOpsDefs(traversal: Traversal.V[Attachment]) {
    def getByAttachmentId(attachmentId: String): Traversal.V[Attachment] = traversal.has(_.attachmentId, attachmentId)

    def visible(implicit authContext: AuthContext): Traversal.V[Attachment] = traversal // TODO

  }
}
