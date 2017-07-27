package connectors.misp

import java.util.Date
import javax.inject.{ Inject, Provider, Singleton }

import akka.NotUsed
import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }
import connectors.misp.JsonFormat._
import models._
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import org.elastic4play.controllers.{ Fields, FileInputValue }
import org.elastic4play.services.{ UserSrv ⇒ _, _ }
import org.elastic4play.utils.RichJson
import org.elastic4play.{ ConflictError, InternalError, NotFoundError }
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.api.{ Configuration, Environment, Logger }
import services._
import java.text.SimpleDateFormat

import scala.collection.immutable
import scala.concurrent.duration.{ DurationInt, DurationLong, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class MispConfig(val interval: FiniteDuration, val connections: Seq[MispConnection]) {

  def this(configuration: Configuration, defaultCaseTemplate: Option[String], globalWS: CustomWSAPI) = this(
    configuration.getMilliseconds("misp.interval").fold(1.hour)(_.millis),

    for {
      cfg ← configuration.getConfig("misp").toSeq
      mispWS = globalWS.withConfig(cfg)

      defaultArtifactTags = cfg.getStringSeq("tags").getOrElse(Nil)
      name ← cfg.subKeys

      mispConnectionConfig ← Try(cfg.getConfig(name)).toOption.flatten.toSeq
      url ← mispConnectionConfig.getString("url")
      key ← mispConnectionConfig.getString("key")
      instanceWS = mispWS.withConfig(mispConnectionConfig)
      artifactTags = mispConnectionConfig.getStringSeq("tags").getOrElse(defaultArtifactTags)
      caseTemplate = mispConnectionConfig.getString("caseTemplate").orElse(defaultCaseTemplate)
    } yield MispConnection(name, url, key, instanceWS, caseTemplate, artifactTags))

  @Inject def this(configuration: Configuration, httpSrv: CustomWSAPI) =
    this(
      configuration,
      configuration.getString("misp.caseTemplate"),
      httpSrv)

  def getConnection(name: String): Option[MispConnection] = connections.find(_.name == name)
}

case class MispConnection(
    name: String,
    baseUrl: String,
    key: String,
    ws: CustomWSAPI,
    caseTemplate: Option[String],
    artifactTags: Seq[String]) {

  private[MispConnection] lazy val logger = Logger(getClass)

  logger.info(
    s"""Add MISP connection $name
       |\turl:           $baseUrl
       |\tproxy:         ${ws.proxy}
       |\tcase template: ${caseTemplate.getOrElse("<not set>")}
       |\tartifact tags: ${artifactTags.mkString}""".stripMargin)

  private[misp] def apply(url: String) =
    ws.url(s"$baseUrl/$url")
      .withHeaders(
        "Authorization" → key,
        "Accept" → "application/json")

}

case class ExportedMispAttribute(tpe: String, category: String, value: Either[String, Attachment], comment: Option[String], status: String)

@Singleton
class MispSrv @Inject() (
    mispConfig: MispConfig,
    alertSrvProvider: Provider[AlertSrv],
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    userSrv: UserSrv,
    attachmentSrv: AttachmentSrv,
    tempSrv: TempSrv,
    eventSrv: EventSrv,
    migrationSrv: MigrationSrv,
    httpSrv: CustomWSAPI,
    environment: Environment,
    lifecycle: ApplicationLifecycle,
    implicit val system: ActorSystem,
    implicit val materializer: Materializer,
    implicit val ec: ExecutionContext) {

  private[misp] val logger = Logger(getClass)
  private[misp] lazy val alertSrv = alertSrvProvider.get

  private[misp] def getInstanceConfig(name: String): Future[MispConnection] = mispConfig.connections
    .find(_.name == name)
    .fold(Future.failed[MispConnection](NotFoundError(s"""Configuration of MISP server "$name" not found"""))) { instanceConfig ⇒
      Future.successful(instanceConfig)
    }

  private[misp] def initScheduler() = {
    val task = system.scheduler.schedule(0.seconds, mispConfig.interval) {
      if (migrationSrv.isReady) {
        logger.info("Update of MISP events is starting ...")
        userSrv
          .inInitAuthContext { implicit authContext ⇒
            synchronize().andThen { case _ ⇒ tempSrv.releaseTemporaryFiles() }
          }
          .onComplete {
            case Success(a) ⇒
              logger.info("Misp synchronization completed")
              a.collect {
                case Failure(t) ⇒ logger.warn(s"Update MISP error", t)
              }
            case Failure(t) ⇒ logger.info("Misp synchronization failed", t)
          }
      }
      else {
        logger.info("MISP synchronization cancel, database is not ready")
      }
    }
    lifecycle.addStopHook { () ⇒
      logger.info("Stopping MISP fetching ...")
      task.cancel()
      Future.successful(())
    }
  }

  initScheduler()
  eventSrv.subscribe(actor(new Act {
    become {
      case UpdateMispAlertArtifact() ⇒
        logger.info("UpdateMispAlertArtifact")
        userSrv
          .inInitAuthContext { implicit authContext ⇒
            updateMispAlertArtifact()
          }
          .onComplete {
            case Success(_)     ⇒ logger.info("Artifacts in MISP alerts updated")
            case Failure(error) ⇒ logger.error("Update MISP alert artifacts error :", error)
          }
        ()
      case msg ⇒
        logger.info(s"Receiving unexpected message: $msg (${msg.getClass})")
    }
  }), classOf[UpdateMispAlertArtifact])
  logger.info("subscribe actor")

  def synchronize()(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] = {
    import org.elastic4play.services.QueryDSL._

    // for each MISP server
    Source(mispConfig.connections.toList)
      // get last synchronization
      .mapAsyncUnordered(1) { mispConnection ⇒
        alertSrv.stats(and("type" ~= "misp", "source" ~= mispConnection.name), Seq(selectMax("lastSyncDate")))
          .map { maxLastSyncDate ⇒ mispConnection → new Date((maxLastSyncDate \ "max_lastSyncDate").as[Long]) }
          .recover { case _ ⇒ mispConnection → new Date(0) }
      }
      .flatMapConcat {
        case (mispConnection, lastSyncDate) ⇒
          synchronize(mispConnection, Some(lastSyncDate))
      }
      .runWith(Sink.seq)
  }

  def fullSynchronize()(implicit authContext: AuthContext): Future[immutable.Seq[Try[Alert]]] = {
    Source(mispConfig.connections.toList)
      .flatMapConcat(mispConnection ⇒ synchronize(mispConnection, None))
      .runWith(Sink.seq)
  }

  def synchronize(mispConnection: MispConnection, lastSyncDate: Option[Date])(implicit authContext: AuthContext): Source[Try[Alert], NotUsed] = {
    logger.info(s"Synchronize MISP ${mispConnection.name} from $lastSyncDate")
    // get events that have been published after the last synchronization
    getEventsFromDate(mispConnection, lastSyncDate.getOrElse(new Date(0)))
      // get related alert
      .mapAsyncUnordered(1) { event ⇒
        logger.trace(s"Looking for alert misp:${event.source}:${event.sourceRef}")
        alertSrv.get("misp", event.source, event.sourceRef)
          .map((event, _))
      }
      .mapAsyncUnordered(1) {
        case (event, alert) ⇒
          logger.trace(s"MISP synchro ${mispConnection.name}, event ${event.sourceRef}, alert ${alert.fold("no alert")(a ⇒ "alert " + a.alertId() + "last sync at " + a.lastSyncDate())}")
          logger.info(s"getting MISP event ${event.source}:${event.sourceRef}")
          getAttributes(mispConnection, event.sourceRef, lastSyncDate.flatMap(_ ⇒ alert.map(_.lastSyncDate())))
            .map((event, alert, _))
      }
      .mapAsyncUnordered(1) {
        // if there is no related alert, create a new one
        case (event, None, attrs) ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has no related alert, create it with ${attrs.size} observable(s)")
          val alertJson = Json.toJson(event).as[JsObject] +
            ("type" → JsString("misp")) +
            ("caseTemplate" → mispConnection.caseTemplate.fold[JsValue](JsNull)(JsString)) +
            ("artifacts" → JsArray(attrs))
          alertSrv.create(Fields(alertJson))
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        // if a related alert exists and we follow it or a fullSync, update it
        case (event, Some(alert), attrs) if alert.follow() || lastSyncDate.isEmpty ⇒
          logger.info(s"MISP event ${event.source}:${event.sourceRef} has related alert, update it with ${attrs.size} observable(s)")
          val alertJson = Json.toJson(event).as[JsObject] -
            "type" -
            "source" -
            "sourceRef" -
            "caseTemplate" -
            "date" +
            ("artifacts" → JsArray(attrs)) +
            // if this is a full synchronization,  don't update alert status
            ("status" → (if (lastSyncDate.isEmpty) Json.toJson(alert.status())
            else alert.status() match {
              case AlertStatus.New ⇒ Json.toJson(AlertStatus.New)
              case _               ⇒ Json.toJson(AlertStatus.Updated)
            }))
          logger.debug(s"Update alert ${alert.id} with\n$alertJson")
          val fAlert = alertSrv.update(alert.id, Fields(alertJson))
          // if a case have been created, update it
          (alert.caze() match {
            case None ⇒ fAlert
            case Some(caze) ⇒
              for {
                a ← fAlert
                // if this is a full synchronization, don't update case status
                caseFields = if (lastSyncDate.isEmpty) Fields(alert.toCaseJson).unset("status")
                else Fields(alert.toCaseJson)
                _ ← caseSrv.update(caze, caseFields)
                _ ← artifactSrv.create(caze, attrs.map(Fields.apply))
              } yield a
          })
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        // if the alert is not followed, do nothing
        case (_, Some(alert), _) ⇒
          Future.successful(Success(alert))
      }
  }

  def getEventsFromDate(mispConnection: MispConnection, fromDate: Date): Source[MispAlert, NotUsed] = {
    val date = fromDate.getTime / 1000
    Source
      .fromFuture {
        mispConnection("events/index")
          .post(Json.obj("searchpublish_timestamp" → date))
      }
      .mapConcat { response ⇒
        val eventJson = Json.parse(response.body)
          .asOpt[Seq[JsValue]]
          .getOrElse {
            logger.warn(s"Invalid MISP event format:\n${response.body}")
            Nil
          }
        val events = eventJson
          .flatMap { j ⇒
            j.asOpt[MispAlert]
              .map(_.copy(source = mispConnection.name))
              .orElse {
                logger.warn(s"MISP event can't be parsed\n$j")
                None
              }
          }

        val eventJsonSize = eventJson.size
        val eventsSize = events.size
        if (eventJsonSize != eventsSize)
          logger.warn(s"MISP returns $eventJsonSize events but only $eventsSize contain valid data")
        events.toList
      }
  }

  def getAttributes(
    mispConnection: MispConnection,
    eventId: String,
    fromDate: Option[Date]): Future[Seq[JsObject]] = {

    val date = fromDate.fold(0L)(_.getTime / 1000)

    mispConnection(s"attributes/restSearch/json")
      .post(Json.obj(
        "request" → Json.obj(
          "timestamp" → date,
          "eventid" → eventId)))
      // add ("deleted" → 1) to see also deleted attributes
      // add ("deleted" → "only") to see only deleted attributes
      .map { response ⇒
        val refDate = fromDate.getOrElse(new Date(0))
        val artifactTags = JsString(s"src:${mispConnection.name}") +: JsArray(mispConnection.artifactTags.map(JsString))
        (Json.parse(response.body) \ "response" \\ "Attribute")
          .flatMap(_.as[Seq[MispAttribute]])
          .filter(_.date after refDate)
          .flatMap {
            case a if a.tpe == "attachment" || a.tpe == "malware-sample" ⇒
              Seq(
                Json.obj(
                  "dataType" → "file",
                  "message" → a.comment,
                  "tags" → (artifactTags.value ++ a.tags.map(JsString)),
                  "remoteAttachment" → Json.obj(
                    "filename" → a.value,
                    "reference" → a.id,
                    "type" → a.tpe),
                  "startDate" → a.date))
            case a ⇒ convertAttribute(a).map { j ⇒
              val tags = artifactTags ++ (j \ "tags").asOpt[JsArray].getOrElse(JsArray(Nil))
              j.setIfAbsent("tlp", 2L) + ("tags" → tags)
            }
          }
      }
  }

  def attributeToArtifact(
    mispConnection: MispConnection,
    alert: Alert,
    attr: JsObject)(implicit authContext: AuthContext): Option[Future[Fields]] = {
    (for {
      dataType ← (attr \ "dataType").validate[String]
      data ← (attr \ "data").validateOpt[String]
      message ← (attr \ "message").validate[String]
      startDate ← (attr \ "startDate").validate[Date]
      attachmentReference ← (attr \ "remoteAttachment" \ "reference").validateOpt[String]
      attachmentType ← (attr \ "remoteAttachment" \ "type").validateOpt[String]
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
      tlp = tags.map(_.toLowerCase)
        .collectFirst {
          case "tlp:white" ⇒ JsNumber(0)
          case "tlp:green" ⇒ JsNumber(1)
          case "tlp:amber" ⇒ JsNumber(2)
          case "tlp:red"   ⇒ JsNumber(3)
        }
        .getOrElse(JsNumber(alert.tlp()))
      fields = Fields.empty
        .set("dataType", dataType)
        .set("message", message)
        .set("startDate", Json.toJson(startDate))
        .set("tags", JsArray(
          tags
            .filterNot(_.toLowerCase.startsWith("tlp:"))
            .map(JsString)))
        .set("tlp", tlp)
      if attachment.isDefined != data.isDefined
    } yield attachment.fold(Future.successful(fields.set("data", data.get)))(_.map { fiv ⇒
      fields.set("attachment", fiv)
    })) match {
      case JsSuccess(r, _) ⇒ Some(r)
      case e: JsError ⇒
        logger.warn(s"Invalid attribute in alert ${alert.id}: $e\n$attr")
        None
    }
  }

  def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext): Future[Case] = {
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        for {
          caseTemplate ← alertSrv.getCaseTemplate(alert, customCaseTemplate)
          caze ← caseSrv.create(Fields(alert.toCaseJson), caseTemplate)
          _ ← importArtifacts(alert, caze)
        } yield caze
    }
  }

  def importArtifacts(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] = {
    for {
      instanceConfig ← getInstanceConfig(alert.source())
      artifacts ← Future.sequence(alert.artifacts().flatMap(attributeToArtifact(instanceConfig, alert, _)))
      _ ← artifactSrv.create(caze, artifacts)
    } yield caze
  }

  def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] = {
    for {
      _ ← importArtifacts(alert, caze)
      description = caze.description() + s"\n  \n#### Merged with MISP event ${alert.title()}"
      updatedCase ← caseSrv.update(caze, Fields.empty.set("description", description))
    } yield updatedCase
  }

  def updateMispAlertArtifact()(implicit authContext: AuthContext): Future[Unit] = {
    import org.elastic4play.services.QueryDSL._
    logger.info("Update MISP attributes in alerts")
    val (alerts, _) = alertSrv.find("type" ~= "misp", Some("all"), Nil)
    alerts.mapAsyncUnordered(5) { alert ⇒
      if (alert.artifacts().nonEmpty) {
        logger.info(s"alert ${alert.id} has artifacts, ignore it")
        Future.successful(alert → Nil)
      }
      else {
        getInstanceConfig(alert.source())
          .flatMap { mcfg ⇒
            getAttributes(mcfg, alert.sourceRef(), None)
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
          alertSrv.update(alert.id, Fields.empty.set("artifacts", JsArray(artifacts)))
            .recover {
              case t ⇒ logger.error(s"Update alert ${alert.id} fail", t)
            }
      }
      .runWith(Sink.ignore)
      .map(_ ⇒ ())
  }

  def extractMalwareAttachment(file: FileInputValue)(implicit authContext: AuthContext): FileInputValue = {
    import scala.collection.JavaConversions._
    try {
      val zipFile = new ZipFile(file.filepath.toFile)

      if (zipFile.isEncrypted)
        zipFile.setPassword("infected")

      // Get the list of file headers from the zip file
      val fileHeaders = zipFile.getFileHeaders.toList.asInstanceOf[List[FileHeader]]
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
        buffer = Array.ofDim[Byte](128)
        len = zipFile.getInputStream(fileNameHeader).read(buffer)
        filename = new String(buffer, 0, len)

        contentFileHeader ← contentFileHeaders
          .headOption
          .orElse {
            logger.warn(s"Format of malware attribute ${file.name} is invalid : content file not found")
            None
          }

        tempFile = tempSrv.newTemporaryFile("misp_malware", file.name)
        _ = logger.info(s"Extract malware file ${file.filepath} in file $tempFile")
        _ = zipFile.extractFile(contentFileHeader, tempFile.getParent.toString, null, tempFile.getFileName.toString)
      } yield FileInputValue(filename, tempFile, "application/octet-stream")).getOrElse(file)
    }
    catch {
      case e: ZipException ⇒
        logger.warn(s"Format of malware attribute ${file.name} is invalid : zip file is unreadable", e)
        file
    }
  }

  def downloadAttachment(
    mispConnection: MispConnection,
    attachmentId: String)(implicit authContext: AuthContext): Future[FileInputValue] = {
    val fileNameExtractor = """attachment; filename="(.*)"""".r

    mispConnection(s"attributes/download/$attachmentId")
      .withMethod("GET")
      .stream()
      .flatMap {
        case response if response.headers.status != 200 ⇒
          val status = response.headers.status
          response.body.runWith(Sink.headOption).flatMap { body ⇒
            val message = body.fold("<no body>")(_.decodeString("UTF-8"))
            logger.warn(s"MISP attachment $attachmentId can't be downloaded (status $status) : $message")
            Future.failed(InternalError(s"MISP attachment $attachmentId can't be downloaded (status $status)"))
          }
        case response ⇒
          val tempFile = tempSrv.newTemporaryFile("misp_attachment", attachmentId)
          response.body
            .runWith(FileIO.toPath(tempFile))
            .map { ioResult ⇒
              if (!ioResult.wasSuccessful) // throw an exception if transfer failed
                throw ioResult.getError
              val contentType = response.headers.headers.getOrElse("Content-Type", Seq("application/octet-stream")).head
              val filename = response.headers.headers
                .get("Content-Disposition")
                .flatMap(_.collectFirst { case fileNameExtractor(name) ⇒ name })
                .getOrElse("noname")
              FileInputValue(filename, tempFile, contentType)
            }
      }
  }

  def exportStatus(caseId: String): Future[Seq[(String, String)]] = {
    import org.elastic4play.services.QueryDSL._
    alertSrv.find(and("type" ~= "misp", "case" ~= caseId), Some("all"), Nil)
      ._1
      .map { alert ⇒
        alert.source() → alert.sourceRef()
      }
      .runWith(Sink.seq)
  }

  def export(mispName: String, caze: Case)(implicit authContext: AuthContext): Future[(String, Seq[ExportedMispAttribute])] = {
    val mispConnection = mispConfig.getConnection(mispName).getOrElse(sys.error("MISP instance not found"))
    val dateFormat = new SimpleDateFormat("yy-MM-dd")

    def buildAttributeList(): Future[Seq[ExportedMispAttribute]] = {
      import org.elastic4play.services.QueryDSL._
      artifactSrv
        .find(parent("case", withId(caze.id)), Some("all"), Nil)
        ._1
        .map { artifact ⇒
          val (category, tpe) = artifact2attribute(artifact.dataType(), artifact.data())
          val value = (artifact.data(), artifact.attachment()) match {
            case (Some(data), None)       ⇒ Left(data)
            case (None, Some(attachment)) ⇒ Right(attachment)
            case _                        ⇒ sys.error("???")
          }
          ExportedMispAttribute(tpe, category, value, artifact.message(), "")
        }
        .runWith(Sink.seq)
    }

    def extractDuplicateAttributes(attributes: Seq[ExportedMispAttribute]): (Seq[ExportedMispAttribute], Seq[ExportedMispAttribute]) = {
      val attrIndex = attributes.zipWithIndex

      val (duplicateAttributes, uniqueAttributes) = attrIndex.partition {
        case (ExportedMispAttribute(category, tpe, value, _, _), index) ⇒ attrIndex.exists {
          case (ExportedMispAttribute(`category`, `tpe`, `value`, _, _), otherIndex) ⇒ otherIndex < index
          case _ ⇒ false
        }
      }
      (duplicateAttributes.map(_._1.copy(status = "Duplicate")), uniqueAttributes.map(_._1))
    }

    def updateStatus(response: JsValue, attributes: Seq[ExportedMispAttribute]): Seq[ExportedMispAttribute] = {
      val messages = (response \ "errors" \ "Attribute")
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

      attributes.zipWithIndex.map {
        case (attr, index) ⇒ attr.copy(status = messages.getOrElse(index.toString, "Ok"))
      }
    }

    def exportFileAttribute(eventId: String, attribute: ExportedMispAttribute): Future[ExportedMispAttribute] = attribute match {
      case ExportedMispAttribute(_, _, Right(attachment), comment, _) ⇒
        attachmentSrv
          .source(attachment.id)
          .runReduce(_ ++ _)
          .flatMap { data ⇒
            val b64data = java.util.Base64.getEncoder.encodeToString(data.toArray[Byte])
            mispConnection("events/upload_sample").post(Json.obj(
              "request" → Json.obj(
                "event_id" → eventId.toInt, // FIXME add checks
                "category" → "Payload delivery",
                "type" → "malware-sample",
                "comment" → comment,
                "files" → Json.arr(
                  Json.obj(
                    "filename" → attachment.name,
                    "data" → b64data)))))
          }
          .map { response ⇒
            val name = (response.json \ "name")
              .asOpt[String]
            val status = name
              .filter(_ == "Success")
              .map(_ ⇒ "Ok")
              .orElse((response.json \ "message").asOpt[String])
              .orElse(name)
              .getOrElse(s"Unexpected MISP response: ${response.status} ${response.statusText}\n${response.body}")
            attribute.copy(status = status, value = Left(attachment.name))
          }
    }

    for {
      exportStatus ← exportStatus(caze.id)
      _ = println(s"exporting ${caze.id} in $mispName")
      _ = println(s"exportStatus=$exportStatus")
      _ ← exportStatus.find(_._1 == mispName).fold(Future.successful(()))(es ⇒ Future.failed(ConflictError(s"Case ${caze.id} has already been exported in MISP ${es._1}#${es._2}", JsObject(Nil))))
      attributes ← buildAttributeList()
      (duplicateAttributes, uniqueAttributes) = extractDuplicateAttributes(attributes)
      (simpleAttributes, fileAttributes) = uniqueAttributes.partition(_.value.isLeft)
      simpleAttributeJson = simpleAttributes.map { attribute ⇒
        Json.obj(
          "type" → attribute.tpe,
          "category" → attribute.category,
          "value" → attribute.value.left.get,
          "comment" → attribute.comment)
      }

      mispEvent = Json.obj(
        "Event" → Json.obj(
          "distribution" → 0,
          "threat_level_id" → caze.severity().toString,
          "analysis" → 0,
          "info" → caze.title(),
          "date" → dateFormat.format(caze.startDate()),
          "published" → false,
          "Attribute" → simpleAttributeJson
        // "Tag" → tags
        // "orgc_id"
        // "org_id"
        // "sharing_group_id"
        ))
      simpleAttributeResponse ← mispConnection("events").post(mispEvent)
      eventId ← (simpleAttributeResponse.json \ "Event" \ "id")
        .asOpt[String]
        .fold[Future[String]](Future.failed(InternalError(s"Unexpected MISP response: ${simpleAttributeResponse.status} ${simpleAttributeResponse.statusText}\n${simpleAttributeResponse.body}")))(Future.successful)
      fileAttributeStatus ← Future.traverse(fileAttributes)(attr ⇒ exportFileAttribute(eventId, attr))
      _ ← alertSrv.create(Fields.empty
        .set("type", "misp")
        .set("source", mispName)
        .set("sourceRef", eventId)
        .set("date", Json.toJson(caze.startDate()))
        .set("lastSyncDate", Json.toJson(new Date(0)))
        .set("case", caze.id)
        .set("title", caze.title())
        .set("description", "Case have been exported to MISP")
        .set("severity", JsNumber(caze.severity()))
        .set("tags", Json.toJson(caze.tags()))
        .set("tlp", JsNumber(caze.tlp()))
        .set("artifacts", JsArray())
        .set("status", "Imported")
        .set("follow", JsBoolean(false)))
      exportedAttributes = updateStatus(simpleAttributeResponse.json, simpleAttributes) ++ duplicateAttributes ++ fileAttributeStatus
    } yield eventId → exportedAttributes
  }

  def convertAttribute(mispAttribute: MispAttribute): Seq[JsObject] = {
    val dataType = attribute2artifact(mispAttribute.tpe)
    val fields = Json.obj(
      "data" → mispAttribute.value,
      "dataType" → dataType,
      "message" → mispAttribute.comment,
      "startDate" → mispAttribute.date,
      "tags" → Json.arr(s"MISP:type=${
        mispAttribute.tpe
      }", s"MISP:category=${
        mispAttribute.category
      }"))

    val types = mispAttribute.tpe.split('|').toSeq
    if (types.length > 1) {
      val values = mispAttribute.value.split('|').toSeq
      val typesValues = types.zipAll(values, "noType", "noValue")
      val additionnalMessage = typesValues
        .map {
          case (t, v) ⇒ s"$t: $v"
        }
        .mkString("\n")
      typesValues.map {
        case (tpe, value) ⇒
          fields +
            ("dataType" → JsString(attribute2artifact(tpe))) +
            ("data" → JsString(value)) +
            ("message" → JsString(mispAttribute.comment + "\n" + additionnalMessage))
      }
    }
    else {
      Seq(fields)
    }
  }

  private def artifact2attribute(dataType: String, data: Option[String]): (String, String) = {
    dataType match {
      case "filename"     ⇒ "Payload delivery" → "filename"
      case "fqdn"         ⇒ "Network activity" → "hostname"
      case "url"          ⇒ "External analysis" → "url"
      case "user-agent"   ⇒ "Network activity" → "user-agent"
      case "domain"       ⇒ "Network activity" → "domain"
      case "ip"           ⇒ "Network activity" → "ip-src"
      case "mail_subject" ⇒ "Payload delivery" → "email-subject"
      case "hash" ⇒ data.fold(0)(_.length) match {
        case 32  ⇒ "Payload delivery" → "md5"
        case 40  ⇒ "Payload delivery" → "sha1"
        case 64  ⇒ "Payload delivery" → "sha256"
        case 56  ⇒ "Payload delivery" → "sha224"
        case 71  ⇒ "Payload delivery" → "sha384"
        case 128 ⇒ "Payload delivery" → "sha512"
        case _   ⇒ "Payload delivery" → "other"
      }
      case "mail"     ⇒ "Payload delivery" → "email-src"
      case "registry" ⇒ "Persistence mechanism" → "regkey"
      case "uri_path" ⇒ "Network activity" → "uri"
      case "regexp"   ⇒ "Other" → "other"
      case "other"    ⇒ "Other" → "other"
      case "file"     ⇒ "Payload delivery" → "malware-sample"
      case _          ⇒ "Other" → "other"
    }
  }

  private def attribute2artifact(tpe: String): String = attribute2artifactLookup.getOrElse(tpe, "other")

  private lazy val attribute2artifactLookup = Map(
    "md5" → "hash",
    "sha1" → "hash",
    "sha256" → "hash",
    "filename" → "filename",
    "pdb" → "other",
    "filename|md5" → "other",
    "filename|sha1" → "other",
    "filename|sha256" → "other",
    "ip-src" → "ip",
    "ip-dst" → "ip",
    "hostname" → "fqdn",
    "domain" → "domain",
    "domain|ip" → "other",
    "email-src" → "mail",
    "email-dst" → "mail",
    "email-subject" → "mail_subject",
    "email-attachment" → "other",
    "float" → "other",
    "url" → "url",
    "http-method" → "other",
    "user-agent" → "user-agent",
    "regkey" → "registry",
    "regkey|value" → "registry",
    "AS" → "other",
    "snort" → "other",
    "pattern-in-file" → "other",
    "pattern-in-traffic" → "other",
    "pattern-in-memory" → "other",
    "yara" → "other",
    "sigma" → "other",
    "vulnerability" → "other",
    "attachment" → "file",
    "malware-sample" → "file",
    "link" → "other",
    "comment" → "other",
    "text" → "other",
    "hex" → "other",
    "other" → "other",
    "named" → "other",
    "mutex" → "other",
    "target-user" → "other",
    "target-email" → "mail",
    "target-machine" → "fqdn",
    "target-org" → "other",
    "target-location" → "other",
    "target-external" → "other",
    "btc" → "other",
    "iban" → "other",
    "bic" → "other",
    "bank-account-nr" → "other",
    "aba-rtn" → "other",
    "bin" → "other",
    "cc-number" → "other",
    "prtn" → "other",
    "threat-actor" → "other",
    "campaign-name" → "other",
    "campaign-id" → "other",
    "malware-type" → "other",
    "uri" → "uri_path",
    "authentihash" → "other",
    "ssdeep" → "hash",
    "imphash" → "hash",
    "pehash" → "hash",
    "impfuzzy" → "hash",
    "sha224" → "hash",
    "sha384" → "hash",
    "sha512" → "hash",
    "sha512/224" → "hash",
    "sha512/256" → "hash",
    "tlsh" → "other",
    "filename|authentihash" → "other",
    "filename|ssdeep" → "other",
    "filename|imphash" → "other",
    "filename|impfuzzy" → "other",
    "filename|pehash" → "other",
    "filename|sha224" → "other",
    "filename|sha384" → "other",
    "filename|sha512" → "other",
    "filename|sha512/224" → "other",
    "filename|sha512/256" → "other",
    "filename|tlsh" → "other",
    "windows-scheduled-task" → "other",
    "windows-service-name" → "other",
    "windows-service-displayname" → "other",
    "whois-registrant-email" → "mail",
    "whois-registrant-phone" → "other",
    "whois-registrant-name" → "other",
    "whois-registrar" → "other",
    "whois-creation-date" → "other",
    "x509-fingerprint-sha1" → "other",
    "dns-soa-email" → "other",
    "size-in-bytes" → "other",
    "counter" → "other",
    "datetime" → "other",
    "cpe" → "other",
    "port" → "other",
    "ip-dst|port" → "other",
    "ip-src|port" → "other",
    "hostname|port" → "other",
    "email-dst-display-name" → "other",
    "email-src-display-name" → "other",
    "email-header" → "other",
    "email-reply-to" → "other",
    "email-x-mailer" → "other",
    "email-mime-boundary" → "other",
    "email-thread-index" → "other",
    "email-message-id" → "other",
    "github-username" → "other",
    "github-repository" → "other",
    "github-organisation" → "other",
    "jabber-id" → "other",
    "twitter-id" → "other",
    "first-name" → "other",
    "middle-name" → "other",
    "last-name" → "other",
    "date-of-birth" → "other",
    "place-of-birth" → "other",
    "gender" → "other",
    "passport-number" → "other",
    "passport-country" → "other",
    "passport-expiration" → "other",
    "redress-number" → "other",
    "nationality" → "other",
    "visa-number" → "other",
    "issue-date-of-the-visa" → "other",
    "primary-residence" → "other",
    "country-of-residence" → "other",
    "special-service-request" → "other",
    "frequent-flyer-number" → "other",
    "travel-details" → "other",
    "payment-details" → "other",
    "place-port-of-original-embarkation" → "other",
    "place-port-of-clearance" → "other",
    "place-port-of-onward-foreign-destination" → "other",
    "passenger-name-record-locator-number" → "other",
    "mobile-application-id" → "other")
}
