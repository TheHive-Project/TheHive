package connectors.misp

import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.{ Inject, Provider, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

import play.api.libs.json._

import akka.stream.scaladsl.Sink
import connectors.misp.JsonFormat.tlpWrites
import models.{ Artifact, Case }
import services.{ AlertSrv, ArtifactSrv }
import JsonFormat.exportedAttributeWrites
import akka.stream.Materializer

import org.elastic4play.{ BadRequestError, InternalError }
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AttachmentSrv, AuthContext }
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
        case (ExportedMispAttribute(_, category, tpe, _, value, _), index) ⇒ attrIndex.exists {
          case (ExportedMispAttribute(_, `category`, `tpe`, _, `value`, _), otherIndex) ⇒ otherIndex >= index
          case _ ⇒ true
        }
      }
      .map(_._1)
  }

  def createEvent(mispConnection: MispConnection, title: String, severity: Long, tlp: Long, date: Date, attributes: Seq[ExportedMispAttribute], extendsEvent: Option[String]): Future[(String, Seq[ExportedMispAttribute])] = {
    val mispEvent = Json.obj(
      "Event" → Json.obj(
        "distribution" → 0,
        "threat_level_id" → (4 - severity),
        "analysis" → 0,
        "info" → title,
        "date" → dateFormat.format(date),
        "published" → false,
        "Attribute" → attributes,
        "Tag" → Json.arr(
          Json.obj("name" → tlpWrites.writes(tlp))),
        "extends_uuid" -> extendsEvent.fold[JsValue](JsNull)(JsString)))
    mispConnection("events")
      .post(mispEvent)
      .map { mispResponse ⇒
        val eventId = (mispResponse.json \ "Event" \ "id")
          .asOpt[String]
          .getOrElse(throw InternalError(s"Unexpected MISP response: ${mispResponse.status} ${mispResponse.statusText}\n${mispResponse.body}"))
        // Get list of attributes that export has failed
        val messages = (mispResponse.json \ "errors" \ "Attribute")
          .asOpt[JsObject]
          .getOrElse(JsObject.empty)
          .fields
          .toMap
          .mapValues { m ⇒
            (m \ "value")
              .asOpt[Seq[String]]
              .flatMap(_.headOption)
              .getOrElse(s"Unexpected message format: $m")
          }
        val exportedAttributes = attributes.zipWithIndex.collect { // keep only attributes that succeed
          case (attr, index) if !messages.contains(index.toString) ⇒ attr
        }
        eventId → exportedAttributes
      }
  }

  def exportAttribute(mispConnection: MispConnection, eventId: String, attribute: ExportedMispAttribute): Future[Artifact] = {
    val mispResponse = attribute match {
      case ExportedMispAttribute(_, _, _, _, Right(attachment), comment) ⇒
        attachmentSrv
          .source(attachment.id)
          .runReduce(_ ++ _)
          .flatMap { data ⇒
            val b64data = java.util.Base64.getEncoder.encodeToString(data.toArray[Byte])
            val body = Json.obj(
              "request" → Json.obj(
                "category" → "Payload delivery",
                "type" → "malware-sample",
                "comment" → comment,
                "files" → Json.arr(
                  Json.obj(
                    "filename" → attachment.name,
                    "data" → b64data))))
            mispConnection(s"events/upload_sample/$eventId").post(body)
          }
      case attr ⇒ mispConnection(s"attributes/add/$eventId").post(Json.toJson(attr))
    }

    mispResponse.map {
      case response if response.status / 100 == 2 ⇒
        // then add tlp tag
        // doesn't work with file artifact (malware sample attribute)
        (response.json \ "Attribute" \ "id").asOpt[String]
          .foreach { attributeId ⇒
            mispConnection("/attributes/addTag")
              .post(Json.obj("attribute" → attributeId, "tag" → tlpWrites.writes(attribute.tlp)))
          }
        attribute.artifact
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
    if (!mispConnection.canExport)
      Future.failed(BadRequestError(s"Export on MISP connection $mispName is denied by configuration"))
    else {
      for {
        (maybeAlertId, maybeEventId) ← relatedMispEvent(mispName, caze.id)
        attributes ← mispSrv.getAttributesFromCase(caze)
        uniqueAttributes = removeDuplicateAttributes(attributes)
        simpleAttributes = uniqueAttributes.filter(_.value.isLeft) // exclude attributes containing file
        (eventId, exportedAttributes) ← createEvent(mispConnection, caze.title(), caze.severity(), caze.tlp(), caze.startDate(), simpleAttributes, maybeEventId)
        initialExportesArtifacts = exportedAttributes.map(a ⇒ Success(a.artifact))
        exportedAttributeValues = exportedAttributes.map(_.value.map(_.name))
        newAttributes = uniqueAttributes.filterNot(attr ⇒ exportedAttributeValues.contains(attr.value.map(_.name)))
        // exportedArtifact ← Future.traverse(newAttributes)(attr ⇒ exportAttribute(mispConnection, eventId, attr).toTry)
        // ** workaround **
        // Wait the end of the MISP request to start the next one, unless, MISP generate an internal error:
        // Error: [PDOException] SQLSTATE[40001]: Serialization failure: 1213 Deadlock found when trying to get lock; try restarting transaction
        exportedArtifact ← newAttributes.foldLeft(Future.successful(Seq.empty[Try[Artifact]])) {
          case (fAcc, attr) ⇒
            for (acc ← fAcc; a ← exportAttribute(mispConnection, eventId, attr).toTry) yield acc :+ a
        }
        // ** end of workaround **
        artifacts = uniqueAttributes.map { a ⇒
          Json.obj(
            "data" → a.artifact.data(),
            "dataType" → a.artifact.dataType(),
            "message" → a.artifact.message(),
            "startDate" → a.artifact.startDate(),
            "attachment" → a.artifact.attachment(),
            "tlp" → a.artifact.tlp(),
            "tags" → a.artifact.tags(),
            "ioc" → a.artifact.ioc())
        }
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
            "artifacts" → artifacts,
            "status" → "Imported",
            "follow" → true)))
        } { alertId ⇒
          alertSrv.update(alertId, Fields(Json.obj(
            "artifacts" → artifacts,
            "status" → "Imported")))
        }
      } yield alert.id → (initialExportesArtifacts ++ exportedArtifact)
    }
  }
}