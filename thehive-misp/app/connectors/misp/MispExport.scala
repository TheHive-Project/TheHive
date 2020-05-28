package connectors.misp

import java.text.SimpleDateFormat
import java.util.Date

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import connectors.misp.JsonFormat.{exportedAttributeWrites, tlpWrites}
import javax.inject.{Inject, Provider, Singleton}
import models.{Artifact, Case}
import org.elastic4play.controllers.Fields
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services.{Attachment, AttachmentSrv, AuthContext}
import org.elastic4play.utils.RichFuture
import org.elastic4play.{BadRequestError, InternalError}
import play.api.Logger
import play.api.libs.json._
import services.AlertSrv

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

@Singleton
class MispExport @Inject()(
    mispConfig: MispConfig,
    mispSrv: MispSrv,
    alertSrvProvider: Provider[AlertSrv],
    attachmentSrv: AttachmentSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends MispConverter {

  lazy val dateFormat             = new SimpleDateFormat("yy-MM-dd")
  private[misp] lazy val alertSrv = alertSrvProvider.get
  lazy val logger: Logger = Logger(getClass)

  def relatedMispEvent(mispName: String, caseId: String): Future[(Option[String], Option[String])] = {
    import org.elastic4play.services.QueryDSL._
    alertSrv
      .find(and("type" ~= "misp", "case" ~= caseId, "source" ~= mispName), Some("0-1"), Nil)
      ._1
      .map { alert ⇒
        alert.id → alert.sourceRef()
      }
      .runWith(Sink.headOption)
      .map(alertIdSource ⇒ alertIdSource.map(_._1) → alertIdSource.map(_._2))
  }

  def removeDuplicateAttributes(attributes: Seq[ExportedMispAttribute]): Seq[ExportedMispAttribute] = {
    var attrSet = Set.empty[(String, String, String)]
    val builder = Seq.newBuilder[ExportedMispAttribute]
    attributes.foreach { attr ⇒
      val tuple = (attr.category, attr.tpe, attr.value.fold(identity, _.name))
      if (!attrSet.contains(tuple)) {
        builder += attr
        attrSet += tuple
      }
    }
    builder.result()
  }

  def createEvent(
      mispConnection: MispConnection,
      title: String,
      severity: Long,
      tlp: Long,
      date: Date,
      attributes: Seq[ExportedMispAttribute],
      extendsEvent: Option[String],
      tags: Seq[String]
  ): Future[(String, Seq[ExportedMispAttribute])] = {
    logger.debug(s"Create MISP event $title, with ${attributes.size} attributes")

    val mispEvent = Json.obj(
      "Event" → Json.obj(
        "distribution"    → 0,
        "threat_level_id" → (4 - severity),
        "analysis"        → 0,
        "info"            → title,
        "date"            → dateFormat.format(date),
        "published"       → false,
        "Attribute"       → attributes,
        "Tag"             → JsArray((tags.map(JsString.apply) :+ tlpWrites.writes(tlp)).map(t ⇒ Json.obj("name" → t))),
        "extends_uuid"    → extendsEvent.fold[JsValue](JsNull)(JsString)
      )
    )
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
      case ExportedMispAttribute(artifact, _, _, _, Right(attachment), comment) ⇒
        attachmentSrv
          .source(attachment.id)
          .runReduce(_ ++ _)
          .flatMap { data ⇒
            val b64data = java.util.Base64.getEncoder.encodeToString(data.toArray[Byte])
            val body = Json.obj(
              "request" → Json.obj(
                "category" → "Payload delivery",
                "type"     → "malware-sample",
                "comment"  → comment,
                "files"    → Json.arr(Json.obj("filename" → attachment.name, "data" → b64data)),
                "to_ids"   → artifact.ioc()
              )
            )
            mispConnection(s"events/upload_sample/$eventId").post(body)
          }
      case attr ⇒ mispConnection(s"attributes/add/$eventId").post(Json.toJson(attr))
    }

    mispResponse.map {
      case response if response.status / 100 == 2 ⇒
        // then add tlp tag
        // doesn't work with file artifact (malware sample attribute)
        (response.json \ "Attribute" \ "id")
          .asOpt[String]
          .foreach { attributeId ⇒
            mispConnection("/attributes/addTag")
              .post(Json.obj("attribute" → attributeId, "tag" → tlpWrites.writes(attribute.tlp)))
          }
        attribute.artifact
      case response ⇒
        val json    = response.json
        val message = (json \ "message").asOpt[String]
        val error   = (json \ "errors" \ "value").head.asOpt[String]
        val errorMessage = for {
          m ← message
          e ← error
        } yield s"$m $e"
        throw MispExportError(
          errorMessage orElse message orElse error getOrElse s"Unexpected MISP response: ${response.status} ${response.statusText}\n${response.body}",
          attribute.artifact
        )
    }
  }

  def getUpdatableEvent(mispConnection: MispConnection, eventId: String): Future[Option[String]] =
    mispConnection(s"/events/getEditStrategy/$eventId")
      .get()
      .map {
        case resp if resp.status / 100 == 2 ⇒
          val body           = resp.json
          val isEditStrategy = (body \ "strategy").asOpt[String].contains("edit")
          if (!isEditStrategy) (body \ "extensions" \ 0 \ "id").asOpt[String]
          else Some(eventId)
        case _ ⇒ Some(eventId)
      }

  def export(mispName: String, caze: Case)(implicit authContext: AuthContext): Future[(String, Seq[Try[Artifact]])] = {
    logger.info(s"Exporting case ${caze.caseId()} to MISP $mispName")
    val mispConnection = mispConfig.getConnection(mispName).getOrElse(sys.error("MISP instance not found"))
    if (!mispConnection.canExport)
      Future.failed(BadRequestError(s"Export on MISP connection $mispName is denied by configuration"))
    else {
      for {
        (maybeAlertId, maybeEventId) ← relatedMispEvent(mispName, caze.id)
        _ = logger.debug(maybeEventId.fold(s"Related MISP event doesn't exist")(e ⇒ s"Related MISP event found : $e"))
        attributes ← mispSrv.getAttributesFromCase(caze)
        uniqueAttributes = removeDuplicateAttributes(attributes)
        eventToUpdate ← maybeEventId.fold(Future.successful[Option[String]](None))(getUpdatableEvent(mispConnection, _))
        (eventId, initialExportesArtifacts, existingAttributes) ← eventToUpdate.fold {
          logger.debug(s"Creating a new MISP event that extends $maybeEventId")
          val simpleAttributes = uniqueAttributes.filter(_.value.isLeft)
          // if no event is associated to this case, create a new one
          createEvent(
            mispConnection,
            caze.title(),
            caze.severity(),
            caze.tlp(),
            caze.startDate(),
            simpleAttributes,
            maybeEventId,
            if (mispConnection.exportCaseTags) caze.tags() else Nil
          ).map {
            case (eventId, exportedAttributes) ⇒
              (eventId, exportedAttributes.map(a ⇒ Success(a.artifact)), exportedAttributes.map(_.value.map(_.name)))
          }
        } { eventId ⇒ // if an event already exists, retrieve its attributes in order to export only new one
          logger.debug(s"Updating MISP event $eventId")
          mispSrv.getAttributesFromMisp(mispConnection, eventId, None).map { attributes ⇒
            (eventId, Nil, attributes.map {
              case MispArtifact(SimpleArtifactData(data), _, _, _, _, _, _)                             ⇒ Left(data)
              case MispArtifact(RemoteAttachmentArtifact(filename, _, _), _, _, _, _, _, _)             ⇒ Right(filename)
              case MispArtifact(AttachmentArtifact(Attachment(filename, _, _, _, _)), _, _, _, _, _, _) ⇒ Right(filename)
            })
          }
        }

        newAttributes = uniqueAttributes.filterNot(attr ⇒ existingAttributes.contains(attr.value.map(_.name)))
        exportedArtifact ← Future.traverse(newAttributes)(attr ⇒ exportAttribute(mispConnection, eventId, attr).toTry)
        artifacts = uniqueAttributes.map { a ⇒
          Json.obj(
            "data"       → a.artifact.data(),
            "dataType"   → a.artifact.dataType(),
            "message"    → a.artifact.message(),
            "startDate"  → a.artifact.startDate(),
            "attachment" → a.artifact.attachment(),
            "tlp"        → a.artifact.tlp(),
            "tags"       → a.artifact.tags(),
            "ioc"        → a.artifact.ioc()
          )
        }
        alert ← maybeAlertId.fold {
          alertSrv.create(
            Fields(
              Json.obj(
                "type"         → "misp",
                "source"       → mispName,
                "sourceRef"    → eventId,
                "date"         → caze.startDate(),
                "lastSyncDate" → new Date(0),
                "case"         → caze.id,
                "title"        → caze.title(),
                "description"  → "Case have been exported to MISP",
                "severity"     → caze.severity(),
                "tags"         → caze.tags(),
                "tlp"          → caze.tlp(),
                "artifacts"    → artifacts,
                "status"       → "Imported",
                "follow"       → true
              )
            )
          )
        } { alertId ⇒
          alertSrv.update(alertId, Fields(Json.obj("artifacts" → artifacts, "status" → "Imported")))
        }
      } yield alert.id → (initialExportesArtifacts ++ exportedArtifact)
    }
  }
}
