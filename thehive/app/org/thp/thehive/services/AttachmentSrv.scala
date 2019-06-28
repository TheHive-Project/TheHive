package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.Files

import scala.concurrent.Future
import scala.util.Try

import play.api.Configuration

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import gremlin.scala.{Graph, GremlinScala, Vertex}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.{StorageSrv, VertexSrv}
import org.thp.scalligraph.{EntitySteps, Hasher}
import org.thp.thehive.models.Attachment

@Singleton
class AttachmentSrv @Inject()(configuration: Configuration, storageSrv: StorageSrv)(implicit db: Database)
    extends VertexSrv[Attachment, AttachmentSteps] {

  val hashers = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AttachmentSteps = new AttachmentSteps(raw)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Try[Attachment with Entity] = {
    val hs     = hashers.fromPath(file.filepath)
    val id     = hs.head.toString
    val is     = Files.newInputStream(file.filepath)
    val result = storageSrv.saveBinary(id, is).map(_ => create(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs, id)))
    is.close()
    result
  }

  def create(filename: String, contentType: String, data: Array[Byte])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Attachment with Entity] = {
    val hs = hashers.fromBinary(data)
    val id = hs.head.toString
    storageSrv.saveBinary(id, data).map(_ => create(Attachment(filename, data.length.toLong, contentType, hs, id)))
  }

  def source(attachment: Attachment with Entity)(implicit graph: Graph): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => stream(attachment))

  def stream(attachment: Attachment with Entity)(implicit graph: Graph): InputStream = storageSrv.loadBinary(attachment._id)
}

@EntitySteps[Attachment]
class AttachmentSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Attachment, AttachmentSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): AttachmentSteps = new AttachmentSteps(raw)

  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }
}
