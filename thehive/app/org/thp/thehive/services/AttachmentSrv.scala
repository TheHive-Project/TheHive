package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.Files

import scala.concurrent.Future
import scala.util.Try

import play.api.Configuration

import akka.NotUsed
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{StorageSrv, VertexSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.models.Attachment

@Singleton
class AttachmentSrv @Inject() (configuration: Configuration, storageSrv: StorageSrv)(implicit db: Database, mat: Materializer)
    extends VertexSrv[Attachment, AttachmentSteps] {

  val hashers: Hasher = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AttachmentSteps = new AttachmentSteps(raw)

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

  def create(filename: String, contentType: String, data: Array[Byte])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary("attachment", id, data).flatMap(_ => createEntity(Attachment(filename, data.length.toLong, contentType, hs, id)))
  }

  def create(filename: String, size: Long, contentType: String, data: Source[ByteString, NotUsed])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary("attachment", id, data).flatMap(_ => createEntity(Attachment(filename, size, contentType, hs, id)))
  }

  override def get(idOrAttachmentId: String)(implicit graph: Graph): AttachmentSteps =
    if (db.isValidId(idOrAttachmentId)) getByIds(idOrAttachmentId)
    else initSteps.getByAttachmentId(idOrAttachmentId)

  def source(attachment: Attachment with Entity): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity): InputStream = storageSrv.loadBinary("attachment", attachment.attachmentId)

  def exists(attachment: Attachment with Entity): Boolean = storageSrv.exists("attachment", attachment.attachmentId)

  def cascadeRemove(attachment: Attachment with Entity)(implicit graph: Graph): Try[Unit] =
    // TODO handle Storage data removal
    Try(get(attachment).remove())

}

@EntitySteps[Attachment]
class AttachmentSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Attachment](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): AttachmentSteps = new AttachmentSteps(newRaw)
  override def newInstance(): AttachmentSteps                             = new AttachmentSteps(raw.clone())

  def getByAttachmentId(attachmentId: String): AttachmentSteps = this.has("attachmentId", attachmentId)

  def visible(implicit authContext: AuthContext): AttachmentSteps = this // TODO
}
