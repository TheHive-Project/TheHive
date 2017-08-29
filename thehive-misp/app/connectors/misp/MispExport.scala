package connectors.misp

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.libs.json.{ JsObject, Json }

import akka.stream.scaladsl.Sink
import models.{ Artifact, Case }
import services.{ AlertSrv, ArtifactSrv }
import JsonFormat.exportedAttributeWrites
import akka.stream.Materializer

import org.elastic4play.InternalError
import org.elastic4play.controllers.Fields
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.{ Attachment, AttachmentSrv, AuthContext }
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.utils.RichFuture

@Singleton
class MispExport @Inject() (
    mispConfig: MispConfig,
    mispSrv: MispSrv,
    artifactSrv: ArtifactSrv,
    alertSrvProvider: Provider[AlertSrv],
    attachmentSrv: AttachmentSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends MispConverter {

  lazy val dateFormat = new SimpleDateFormat("yy-MM-dd")
  private[misp] lazy val alertSrv = alertSrvProvider.get

  def relatedMispEvent(mispName: String, caseId: String): Future[(Option[String], Option[String])] = {
    import org.elastic4play.services.QueryDSL._
    alertSrv.find(and("type" ~= "misp", "case" ~= caseId, "source" ~= mispName), Some("0-1"), Nil)
      ._1
      .map { alert ⇒ alert.id → alert.sourceRef() }
      .runWith(Sink.headOption)
      .map(alertIdSource ⇒ alertIdSource.map(_._1) → alertIdSource.map(_._2))
  }

  def removeDuplicateAttributes(attributes: Seq[ExportedMispAttribute]): Seq[ExportedMispAttribute] = {
    val attrIndex = attributes.zipWithIndex

    attrIndex
      .filter {
        case (ExportedMispAttribute(_, category, tpe, value, _), index) ⇒ attrIndex.exists {
          case (ExportedMispAttribute(_, `category`, `tpe`, `value`, _), otherIndex) ⇒ otherIndex >= index
          case _ ⇒ true
        }
      }
      .map(_._1)
  }

  def createEvent(mispConnection: MispConnection, title: String, severity: Long, date: Date, attributes: Seq[ExportedMispAttribute]): Future[(String, Seq[ExportedMispAttribute])] = {
    val mispEvent = Json.obj(
      "Event" → Json.obj(
        "distribution" → 0,
        "threat_level_id" → (4 - severity),
        "analysis" → 0,
        "info" → title,
        "date" → dateFormat.format(date),
        "published" → false,
        "Attribute" → attributes))
    mispConnection("events")
      .post(mispEvent)
      .map { mispResponse ⇒
        val eventId = (mispResponse.json \ "Event" \ "id")
          .asOpt[String]
          .getOrElse(throw InternalError(s"Unexpected MISP response: ${mispResponse.status} ${mispResponse.statusText}\n${mispResponse.body}"))
        val messages = (mispResponse.json \ "errors" \ "Attribute")
          .asOpt[JsObject]
          .getOrElse(JsObject(Nil))
          .fields
          .toMap
          .mapValues { m ⇒
            (m \ "value")
              .asOpt[Seq[String]]
              .flatMap(_.headOption)
              .getOrElse(s"Unexpected message format: $m")
          }
        val exportedAttributes = attributes.zipWithIndex.collect {
          case (attr, index) if !messages.contains(index.toString) ⇒ attr
        }
        eventId → exportedAttributes
      }
  }

  def exportAttribute(mispConnection: MispConnection, eventId: String, attribute: ExportedMispAttribute): Future[Artifact] = {
    val mispResponse = attribute match {
      case ExportedMispAttribute(_, _, _, Right(attachment), comment) ⇒
        attachmentSrv
          .source(attachment.id)
          .runReduce(_ ++ _)
          .flatMap { data ⇒
            val b64data = java.util.Base64.getEncoder.encodeToString(data.toArray[Byte])
            val body = Json.obj(
              "request" → Json.obj(
                "event_id" → eventId.toInt,
                "category" → "Payload delivery",
                "type" → "malware-sample",
                "comment" → comment,
                "files" → Json.arr(
                  Json.obj(
                    "filename" → attachment.name,
                    "data" → b64data))))
            mispConnection("events/upload_sample").post(body)
          }
      case attr ⇒ mispConnection(s"attributes/add/$eventId").post(Json.toJson(attr))

    }

    mispResponse.map {
      case response if response.status / 100 == 2 ⇒ attribute.artifact
      case response ⇒
        val json = response.json
        val message = (json \ "message").asOpt[String]
        val error = (json \ "errors" \ "value").head.asOpt[String]
        val errorMessage = for (m ← message; e ← error) yield s"$m $e"
        throw MispExportError(errorMessage orElse message orElse error getOrElse s"Unexpected MISP response: ${response.status} ${response.statusText}\n${response.body}", attribute.artifact)
    }
  }

  def export(mispName: String, caze: Case)(implicit authContext: AuthContext): Future[(String, Seq[Try[Artifact]])] = {
    val mispConnection = mispConfig.getConnection(mispName).getOrElse(sys.error("MISP instance not found"))

    for {
      (maybeAlertId, maybeEventId) ← relatedMispEvent(mispName, caze.id)
      _ = println(s"[0] $maybeAlertId $maybeEventId")
      attributes ← mispSrv.getAttributesFromCase(caze)
      _ = println(s"[1] $attributes")
      uniqueAttributes = removeDuplicateAttributes(attributes)
      _ = println(s"[2] $uniqueAttributes")
      (eventId, existingAttributes) ← maybeEventId.fold {
        val simpleAttributes = uniqueAttributes.filter(_.value.isLeft)
        println(s"[3] $simpleAttributes")
        // if no event is associated to this case, create a new one
        createEvent(mispConnection, caze.title(), caze.severity(), caze.startDate(), simpleAttributes).map {
          case (eventId, exportedAttributes) ⇒ eventId → exportedAttributes.map(_.value.map(_.name))
        }
      } { eventId ⇒ // if an event already exists, retrieve its attributes in order to export only new one
        mispSrv.getAttributesFromMisp(mispConnection, eventId, None).map { attributes ⇒
          eventId → attributes.map {
            case MispArtifact(SimpleArtifactData(data), _, _, _, _, _)                             ⇒ Left(data)
            case MispArtifact(RemoteAttachmentArtifact(filename, _, _), _, _, _, _, _)             ⇒ Right(filename)
            case MispArtifact(AttachmentArtifact(Attachment(filename, _, _, _, _)), _, _, _, _, _) ⇒ Right(filename)
          }
        }
      }
      _ = println(s"[4] $existingAttributes")
      newAttributes = uniqueAttributes.filterNot(attr ⇒ existingAttributes.contains(attr.value.map(_.name)))
      _ = println(s"[5] $newAttributes")
      exportedArtifact ← Future.traverse(newAttributes)(attr ⇒ exportAttribute(mispConnection, eventId, attr).toTry)
      _ = println(s"[6] $exportedArtifact")
      alert ← maybeAlertId.fold {
        alertSrv.create(Fields(Json.obj(
          "type" → "misp",
          "source" → mispName,
          "sourceRef" → eventId,
          "date" → caze.startDate(),
          "lastSyncDate" → new Date(0),
          "case" → caze.id,
          "title" → caze.title(),
          "description" → "Case have been exported to MISP",
          "severity" → caze.severity(),
          "tags" → caze.tags(),
          "tlp" → caze.tlp(),
          "artifacts" → uniqueAttributes.map(_.artifact),
          "status" → "Imported",
          "follow" → false)))
      } { alertId ⇒
        val artifacts = uniqueAttributes.map { exportedArtifact ⇒
          Json.obj(
            "data" → exportedArtifact.artifact.data(),
            "dataType" → exportedArtifact.artifact.dataType(),
            "message" → exportedArtifact.artifact.message(),
            "startDate" → exportedArtifact.artifact.startDate(),
            "attachment" → exportedArtifact.artifact.attachment(),
            "tlp" → exportedArtifact.artifact.tlp(),
            "tags" → exportedArtifact.artifact.tags(),
            "ioc" → exportedArtifact.artifact.ioc())
        }
        alertSrv.update(alertId, Fields(Json.obj(
          "artifacts" → artifacts,
          "status" → "Imported")))
      }
    } yield alert.id → exportedArtifact
  }
}
