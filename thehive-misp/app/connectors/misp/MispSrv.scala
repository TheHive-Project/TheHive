package connectors.misp

import java.util.Date

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source}
import connectors.misp.JsonFormat._
import javax.inject.{Inject, Provider, Singleton}
import models._
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import org.elastic4play.controllers.{Fields, FileInputValue}
import org.elastic4play.services.{Attachment, AuthContext, TempSrv}
import org.elastic4play.{InternalError, NotFoundError}
import play.api.Logger
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue
import services._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class MispSrv @Inject()(
    mispConfig: MispConfig,
    alertSrvProvider: Provider[AlertSrv],
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    tempSrv: TempSrv,
    implicit val mat: Materializer
) extends MispConverter {

  private[misp] lazy val logger   = Logger(getClass)
  private[misp] lazy val alertSrv = alertSrvProvider.get

  private[misp] def getInstanceConfig(name: String): Future[MispConnection] =
    mispConfig
      .connections
      .find(_.name == name)
      .fold(Future.failed[MispConnection](NotFoundError(s"""Configuration of MISP server "$name" not found"""))) { instanceConfig ⇒
        Future.successful(instanceConfig)
      }

  def getEvent(mispConnection: MispConnection, eventId: String)(implicit ec: ExecutionContext): Future[MispAlert] = {
    logger.debug(s"Get MISP event $eventId")
    require(!eventId.isEmpty)
    mispConnection(s"events/$eventId")
      .get()
      .map { e ⇒
        (e.json \ "Event")
          .as[MispAlert]
          .copy(source = mispConnection.name)
      }
  }

  def getEventsFromDate(mispConnection: MispConnection, fromDate: Date): Source[MispAlert, NotUsed] = {
    logger.debug(s"Get MISP events from $fromDate")
    val date = fromDate.getTime / 1000
    Source
      .future {
        mispConnection("events/index")
          .post(Json.obj("searchpublish_timestamp" → date))
      }
      .mapConcat { response ⇒
        val eventJson = Try {
          response
            .body[JsValue]
            .as[Seq[JsValue]]
        }.getOrElse {
          logger.warn(s"Invalid MISP event format:\n${response.body}")
          Nil
        }
        val events = eventJson
          .flatMap { j ⇒
            j.asOpt[MispAlert]
              .orElse {
                logger.warn(s"MISP event can't be parsed\n$j")
                None
              }
              .filterNot(mispConnection.isExcluded)
              .map(_.copy(source = mispConnection.name))
          }

        val eventJsonSize = eventJson.size
        val eventsSize    = events.size
        if (eventJsonSize != eventsSize)
          logger.warn(s"MISP returns $eventJsonSize events but only $eventsSize contain valid data")
        events.filter(_.lastSyncDate after fromDate).toList
      }
  }

  def getAttributesFromCase(caze: Case)(implicit ec: ExecutionContext): Future[Seq[ExportedMispAttribute]] = {
    import org.elastic4play.services.QueryDSL._
    artifactSrv
      .find(and(withParent(caze), "status" ~= "Ok", "ioc" ~= true), Some("all"), Nil)
      ._1
      .map { artifact ⇒
        val (category, tpe) = fromArtifact(artifact.dataType(), artifact.data())
        val value = (artifact.data(), artifact.attachment()) match {
          case (Some(data), None)       ⇒ Left(data)
          case (None, Some(attachment)) ⇒ Right(attachment)
          case _ ⇒
            logger.error(s"Artifact $artifact has neither data nor attachment")
            sys.error("???")
        }
        ExportedMispAttribute(artifact, tpe, category, artifact.tlp(), value, artifact.message())
      }
      .runWith(Sink.seq)
  }

  def getAttributesFromMisp(mispConnection: MispConnection, eventId: String, fromDate: Option[Date])(
      implicit ec: ExecutionContext
  ): Future[Seq[MispArtifact]] = {

    val date = fromDate.fold(0L)(_.getTime / 1000)

    mispConnection(s"attributes/restSearch/json")
      .post(Json.obj("request" → Json.obj("timestamp" → date, "eventid" → eventId)))
      // add ("deleted" → 1) to see also deleted attributes
      // add ("deleted" → "only") to see only deleted attributes
      .map(_.body)
      .map {
        case body if mispConnection.maxSize.fold(false)(body.length > _) ⇒
          logger.debug(s"Size of event exceeds (${body.length}) the configured limit")
          JsObject.empty
        case body ⇒ Json.parse(body)
      }
      .map { jsBody ⇒
        val refDate      = fromDate.getOrElse(new Date(0))
        val artifactTags = s"src:${mispConnection.name}" +: mispConnection.artifactTags
        (jsBody \ "response" \\ "Attribute")
          .flatMap(_.as[Seq[MispAttribute]])
          .filter(_.date after refDate)
          .flatMap(convertAttribute)
          .groupBy {
            case MispArtifact(SimpleArtifactData(data), dataType, _, _, _, _, _)                             ⇒ dataType → Right(data)
            case MispArtifact(RemoteAttachmentArtifact(filename, _, _), dataType, _, _, _, _, _)             ⇒ dataType → Left(filename)
            case MispArtifact(AttachmentArtifact(Attachment(filename, _, _, _, _)), dataType, _, _, _, _, _) ⇒ dataType → Left(filename)
          }
          .values
          .map { mispArtifact ⇒
            mispArtifact.head.copy(tags = (mispArtifact.head.tags ++ artifactTags).distinct, tlp = 2L)
          }
          .toSeq
      }
  }

  def attributeToArtifact(mispConnection: MispConnection, attr: JsObject, defaultTlp: Long)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Option[Future[Fields]] =
    (for {
      dataType            ← (attr \ "dataType").validate[String]
      data                ← (attr \ "data").validateOpt[String]
      message             ← (attr \ "message").validate[String]
      startDate           ← (attr \ "startDate").validate[Date]
      attachmentReference ← (attr \ "remoteAttachment" \ "reference").validateOpt[String]
      attachmentType      ← (attr \ "remoteAttachment" \ "type").validateOpt[String]
      attachment = attachmentReference
        .flatMap {
          case ref if dataType == "file" ⇒ Some(downloadAttachment(mispConnection, ref))
          case _                         ⇒ None
        }
        .map {
          case f if attachmentType.contains("malware-sample") ⇒ f.map(extractMalwareAttachment)
          case f                                              ⇒ f
        }
      tags = (attr \ "tags").asOpt[Seq[String]].getOrElse(Nil)
      tlp = tags
        .map(_.toLowerCase)
        .collectFirst {
          case "tlp:white" ⇒ JsNumber(0)
          case "tlp:green" ⇒ JsNumber(1)
          case "tlp:amber" ⇒ JsNumber(2)
          case "tlp:red"   ⇒ JsNumber(3)
        }
        .getOrElse(JsNumber(defaultTlp))
      fields = Fields
        .empty
        .set("dataType", dataType)
        .set("message", message)
        .set("startDate", Json.toJson(startDate))
        .set(
          "tags",
          JsArray(
            tags
              .filterNot(_.toLowerCase.startsWith("tlp:"))
              .map(JsString)
          )
        )
        .set("tlp", tlp)
      if (attachment.isDefined && data.isEmpty) || (dataType != "file" && data.isDefined)
    } yield attachment.fold(Future.successful(fields.set("data", data.get)))(_.map { fiv ⇒
      fields.set("attachment", fiv)
    })) match {
      case JsSuccess(r, _) ⇒ Some(r)
      case e: JsError ⇒
        logger.warn(s"Invalid attribute format: $e\n$attr")
        None
    }

  def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        for {
          caseTemplate ← alertSrv.getCaseTemplate(customCaseTemplate)
          caze         ← caseSrv.create(Fields(alert.toCaseJson), caseTemplate)
          _            ← importArtifacts(alert, caze)
        } yield caze
    }

  def importArtifacts(alert: Alert, caze: Case)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    for {
      instanceConfig ← getInstanceConfig(alert.source())
      artifacts      ← Future.sequence(alert.artifacts().flatMap(attributeToArtifact(instanceConfig, _, alert.tlp())))
      _              ← artifactSrv.create(caze, artifacts)
    } yield caze

  def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    for {
      _ ← importArtifacts(alert, caze)
      description = caze.description() + s"\n  \n#### Merged with MISP event ${alert.title()}\n\n${alert.description().trim}"
      updatedCase ← caseSrv.update(caze, Fields.empty.set("description", description))
    } yield updatedCase

  def updateMispAlertArtifact()(implicit authContext: AuthContext, ec: ExecutionContext): Future[Unit] = {
    import org.elastic4play.services.QueryDSL._
    logger.info("Update MISP attributes in alerts")
    val (alerts, _) = alertSrv.find("type" ~= "misp", Some("all"), Nil)
    alerts
      .mapAsyncUnordered(5) { alert ⇒
        if (alert.artifacts().nonEmpty) {
          logger.info(s"alert ${alert.id} has artifacts, ignore it")
          Future.successful(alert → Nil)
        } else {
          getInstanceConfig(alert.source())
            .flatMap { mcfg ⇒
              getAttributesFromMisp(mcfg, alert.sourceRef(), None)
            }
            .map(alert → _)
            .recover {
              case NotFoundError(m) ⇒
                logger.error(s"Retrieve MISP attribute of event ${alert.id} error: $m")
                alert → Nil
              case error ⇒
                logger.error(s"Retrieve MISP attribute of event ${alert.id} error", error)
                alert → Nil
            }
        }
      }
      .filterNot(_._2.isEmpty)
      .mapAsyncUnordered(5) {
        case (alert, artifacts) ⇒
          logger.info(s"Updating alert ${alert.id}")
          alertSrv
            .update(alert.id, Fields.empty.set("artifacts", Json.toJson(artifacts)))
            .recover {
              case t ⇒ logger.error(s"Update alert ${alert.id} fail", t)
            }
      }
      .runWith(Sink.ignore)
      .map(_ ⇒ ())
  }

  def extractMalwareAttachment(file: FileInputValue)(implicit authContext: AuthContext): FileInputValue = {
    import scala.collection.JavaConverters._
    try {
      val zipFile = new ZipFile(file.filepath.toFile)

      if (zipFile.isEncrypted)
        zipFile.setPassword("infected".toCharArray)

      // Get the list of file headers from the zip file
      val fileHeaders = zipFile.getFileHeaders.asScala.toList
      val (fileNameHeaders, contentFileHeaders) = fileHeaders.partition { fileHeader ⇒
        fileHeader.getFileName.endsWith(".filename.txt")
      }
      (for {
        fileNameHeader ← fileNameHeaders
          .headOption
          .orElse {
            logger.warn(s"Format of malware attribute ${file.name} is invalid : file containing filename not found")
            None
          }
        buffer   = Array.ofDim[Byte](128)
        len      = zipFile.getInputStream(fileNameHeader).read(buffer)
        filename = new String(buffer, 0, len)

        contentFileHeader ← contentFileHeaders
          .headOption
          .orElse {
            logger.warn(s"Format of malware attribute ${file.name} is invalid : content file not found")
            None
          }

        tempFile = tempSrv.newTemporaryFile("misp", "malware")
        _        = logger.info(s"Extract malware file ${file.filepath} in file $tempFile")
        _        = zipFile.extractFile(contentFileHeader, tempFile.getParent.toString, tempFile.getFileName.toString)
      } yield FileInputValue(filename, tempFile, "application/octet-stream")).getOrElse(file)
    } catch {
      case e: ZipException ⇒
        logger.warn(s"Format of malware attribute ${file.name} is invalid : zip file is unreadable", e)
        file
    }
  }

  private[MispSrv] val fileNameExtractor = """attachment; filename="(.*)"""".r

  def downloadAttachment(
      mispConnection: MispConnection,
      attachmentId: String
  )(implicit authContext: AuthContext, ec: ExecutionContext): Future[FileInputValue] =
    mispConnection(s"attributes/download/$attachmentId")
      .withMethod("GET")
      .stream()
      .flatMap {
        case response if response.status != 200 ⇒
          val status = response.status
          logger.warn(s"MISP attachment $attachmentId can't be downloaded (status $status) : ${response.body}")
          Future.failed(InternalError(s"MISP attachment $attachmentId can't be downloaded (status $status)"))
        case response ⇒
          val tempFile = tempSrv.newTemporaryFile("misp_attachment", attachmentId)
          response
            .bodyAsSource
            .runWith(FileIO.toPath(tempFile))
            .map { _ ⇒
              val contentType = response.headers.getOrElse("Content-Type", Seq("application/octet-stream")).head
              val filename = response
                .headers
                .get("Content-Disposition")
                .flatMap(_.collectFirst { case fileNameExtractor(name) ⇒ name })
                .getOrElse("noname")
              FileInputValue(filename, tempFile, contentType)
            }
      }
}
