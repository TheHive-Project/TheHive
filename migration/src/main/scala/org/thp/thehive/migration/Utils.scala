package org.thp.thehive.migration
import java.io.InputStream
import java.util.{Date, UUID}

import play.api.Logger
import play.api.libs.json._

import gremlin.scala._
import org.thp.scalligraph.Hasher
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.StorageSrv
import org.thp.thehive.models._
import org.thp.thehive.services.AttachmentSrv

import org.elastic4play.services.{Attachment ⇒ ElasticAttachment, AttachmentSrv ⇒ ElasticAttachmentSrv}

trait Utils {
  val logger: Logger = Logger(getClass)

  implicit class StringIndenter(s: String) {
    def indent(count: Int = 4): String = {
      val ind = " " * count
      ind + s.replaceAll("\n", "\n" + ind)
    }
  }

  def formatError(errors: Seq[(JsPath, Seq[JsonValidationError])]): String =
    errors
      .map {
        case (path, e) ⇒ path.toString() + ":" + e.map(_.messages.mkString(",")).mkString(";")
      }
      .mkString("\n")

  def extractCustomFields(js: JsObject): Seq[(String, Option[Any])] =
    js.fields.flatMap {
      case (name, typeValue: JsObject) ⇒
        typeValue.fields.collectFirst {
          case ("number", JsNumber(value))   ⇒ (name, Some(value.toInt))
          case ("string", JsString(value))   ⇒ (name, Some(value))
          case ("date", JsNumber(value))     ⇒ (name, Some(new Date(value.toLong)))
          case ("boolean", JsBoolean(value)) ⇒ (name, Some(value))
          case ("number", _)                 ⇒ (name, None)
          case ("string", _)                 ⇒ (name, None)
          case ("date", _)                   ⇒ (name, None)
          case ("boolean", _)                ⇒ (name, None)
        }
      case _ ⇒ Nil
    }

  def extractMetrics(js: JsObject): Seq[(String, Option[Int])] =
    js.fields.collect {
      case (name, JsNumber(value)) ⇒ (name, Some(value.toInt))
    }

  def catchError[A](entityType: String, entity: JsObject, progress: ProgressBar)(body: ⇒ Unit): Unit =
    try {
      body
    } catch {
      case JsResultException(e) ⇒
        val message = s"[$entityType] Unable to convert input data:\n${Json.prettyPrint(entity).indent()}\n  Errors:\n${formatError(e).indent()}"
        progress.message(message)
        logger.error(message)
      case t: Throwable ⇒
        val message = s"[$entityType] Import failure:${Json.prettyPrint(entity).indent()}"
        progress.message(message, t)
        logger.error(message, t)
    }

  def saveAttachment(
      attachmentSrv: AttachmentSrv,
      storageSrv: StorageSrv,
      elasticAttachmentSrv: ElasticAttachmentSrv,
      hashers: Hasher,
      toDB: Database)(attachment: ElasticAttachment)(implicit graph: Graph, authContext: AuthContext): Attachment with Entity = {

    def readStream[A](f: InputStream ⇒ A) = {
      val is = elasticAttachmentSrv.stream(attachment.id)
      try f(is)
      finally is.close()
    }

    val hs   = readStream(hashers.fromInputStream)
    val id   = hs.mkString("|") // TODO only one hash ?
    val data = readStream(storageSrv.saveBinary(id, _))

    val attach           = attachmentSrv.create(Attachment(attachment.name, attachment.size, attachment.contentType, hs))
    val attachmentVertex = graph.V().has(Key("_id") of attach._id).head()
    attachmentVertex.addEdge("nextChunk", data, "_id", UUID.randomUUID().toString)
    attach
  }
}
