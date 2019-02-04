package org.thp.thehive.services

import java.io.InputStream
import java.nio.file.Files

import scala.concurrent.Future

import play.api.Configuration

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import gremlin.scala.{Graph, GremlinScala, Key, Vertex, _}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.{EntitySteps, Hasher}
import org.thp.thehive.models.Attachment

@Singleton
class AttachmentSrv @Inject()(configuration: Configuration)(implicit db: Database) extends VertexSrv[Attachment, AttachmentSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AttachmentSteps = new AttachmentSteps(raw)
//  val edgeSrv = new EdgeSrv[Unit, Attachment, ]
  //lass EdgeSrv[E <: Product: ru.TypeTag, FROM <: Product: ru.TypeTag, TO <: Product: ru.TypeTag]

  val hashers = Hasher(configuration.get[Seq[String]]("attachment.hash"): _*)

  def create(file: FFile)(implicit graph: Graph, authContext: AuthContext): Attachment with Entity = {
    val hs   = hashers.fromPath(file.filepath)
    val id   = hs.mkString("|") // TODO only one hash ?
    val is   = Files.newInputStream(file.filepath)
    val data = db.saveBinary(id, is)
    is.close()
    val attachment       = create(Attachment(file.filename, Files.size(file.filepath), file.contentType, hs))
    val attachmentVertex = graph.V().has(Key("_id") of attachment._id).head()
    attachmentVertex.addEdge("nextChunk", data)
    attachment
  }

  def source(attachment: Attachment with Entity)(implicit graph: Graph): Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() ⇒ stream(attachment))

  def stream(attachment: Attachment with Entity)(implicit graph: Graph): InputStream =
    db.loadBinary(graph.vertices(attachment._id).next) // FIXME
}

@EntitySteps[Attachment]
class AttachmentSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Attachment, AttachmentSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): AttachmentSteps = new AttachmentSteps(raw)
}
