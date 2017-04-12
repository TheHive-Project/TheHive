package connectors.misp

import java.nio.file.{ Path, Paths }
import java.text.SimpleDateFormat
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
import org.elastic4play.services._
import org.elastic4play.utils.RichJson
import org.elastic4play.{ InternalError, NotFoundError }
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.ahc.{ AhcWSAPI, AhcWSClientConfig }
import play.api.libs.ws.ssl.{ SSLConfig, TrustManagerConfig, TrustStoreConfig }
import play.api.{ Configuration, Environment, Logger }
import services.{ AlertSrv, ArtifactSrv, CaseSrv }

import scala.concurrent.duration.{ DurationInt, DurationLong, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

case class MispInstanceConfig(
  name: String,
  url: String,
  key: String,
  caseTemplate: Option[String],
  artifactTags: Seq[String])

object MispInstanceConfig {
  def apply(
    name: String,
    defaultCaseTemplate: Option[String],
    configuration: Configuration): Option[MispInstanceConfig] =
    for {
      url ← configuration.getString("url")
      key ← configuration.getString("key")
      tags = configuration.getStringSeq("tags").getOrElse(Nil)
    } yield MispInstanceConfig(name, url, key, configuration.getString("caseTemplate") orElse defaultCaseTemplate, tags)
}

case class MispConfig(truststore: Option[Path], interval: FiniteDuration, instances: Seq[MispInstanceConfig]) {

  def this(configuration: Configuration, defaultCaseTemplate: Option[String]) = this(
    configuration.getString("misp.cert").map(p ⇒ Paths.get(p)),
    configuration.getMilliseconds("misp.interval").fold(1.hour)(_.millis),
    for {
      cfg ← configuration.getConfig("misp").toSeq
      key ← cfg.subKeys
      c ← Try(cfg.getConfig(key)).toOption.flatten.toSeq
      mic ← MispInstanceConfig(key, defaultCaseTemplate, c)
    } yield mic)

  @Inject def this(configuration: Configuration) =
    this(configuration, configuration.getString("misp.caseTemplate"))
}

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
    environment: Environment,
    lifecycle: ApplicationLifecycle,
    implicit val system: ActorSystem,
    implicit val materializer: Materializer,
    implicit val ec: ExecutionContext) {

  private[misp] val logger = Logger(getClass)
  private[misp] lazy val alertSrv = alertSrvProvider.get

  private[misp] val ws = {
    val config = mispConfig.truststore match {
      case Some(p) ⇒ AhcWSClientConfig(
        wsClientConfig = WSClientConfig(
          ssl = SSLConfig(
            trustManagerConfig = TrustManagerConfig(
              trustStoreConfigs = Seq(TrustStoreConfig(filePath = Some(p.toString), data = None))))))
      case None ⇒ AhcWSClientConfig()
    }
    new AhcWSAPI(environment, config, lifecycle)
  }

  private[misp] def getInstanceConfig(name: String): Future[MispInstanceConfig] = mispConfig.instances
    .find(_.name == name)
    .fold(Future.failed[MispInstanceConfig](NotFoundError(s"""Configuration of MISP server "$name" not found"""))) { instanceConfig ⇒
      Future.successful(instanceConfig)
    }

  private[misp] def initScheduler() = {
    val task = system.scheduler.schedule(0.seconds, mispConfig.interval) {
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
    Source(mispConfig.instances.toList)
      // get last synchronization
      .mapAsyncUnordered(5) { mcfg ⇒
        alertSrv.stats(and("type" ~= "misp", "source" ~= mcfg.name), Seq(selectMax("lastSyncDate")))
          .map { maxLastSyncDate ⇒ mcfg → new Date((maxLastSyncDate \ "max_lastSyncDate").as[Long]) }
          .recover { case _ ⇒ mcfg → new Date(0) }
      }
      // get events that have been published after the last synchronization
      .flatMapConcat {
        case (mcfg, lastSyncDate) ⇒
          getEventsFromDate(mcfg, lastSyncDate).map((mcfg, lastSyncDate, _))
      }
      // get related alert
      .mapAsyncUnordered(1) {
        case (mcfg, lastSyncDate, event) ⇒
          alertSrv.get("misp", event.source, event.sourceRef)
            .map(a ⇒ (mcfg, lastSyncDate, event, a))
      }
      .mapAsyncUnordered(1) {
        case (mcfg, lastSyncDate, event, alert) ⇒
          logger.info(s"getting MISP event ${event.sourceRef}")
          getAttributes(mcfg, event.sourceRef, Some(lastSyncDate)).map((mcfg, lastSyncDate, event, alert, _))
      }
      .mapAsyncUnordered(1) {
        // if there is no related alert, create a new one
        case (mcfg, _, event, None, attrs) ⇒
          logger.info(s"MISP event ${event.sourceRef} has no related alert, create it")
          val alertJson = Json.toJson(event).as[JsObject] +
            ("type" → JsString("misp")) +
            ("caseTemplate" → mcfg.caseTemplate.fold[JsValue](JsNull)(JsString)) +
            ("artifacts" → JsArray(attrs))
          alertSrv.create(Fields(alertJson))
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }

        // if a related alert exists, update it
        case (mcfg, lastSyncDate, event, Some(alert), attrs) ⇒
          logger.info(s"MISP event ${event.sourceRef} has related alert, update it")
          val alertJson = Json.toJson(event).as[JsObject] -
            "type" -
            "source" -
            "sourceRef" -
            "date" +
            ("artifacts" → JsArray(attrs)) +
            ("status" → (alert.status() match {
              case AlertStatus.New      ⇒ JsString("New")
              case AlertStatus.Update   ⇒ JsString("Update")
              case AlertStatus.Ignore   ⇒ JsString("Ignore")
              case AlertStatus.Imported ⇒ JsString("Update")
            }))
          val fAlert = alertSrv.update(alert.id, Fields(alertJson))
          // if a case have been created, update it
          (alert.caze() match {
            case None ⇒ fAlert
            case Some(caze) ⇒
              for {
                a ← fAlert
                caze ← caseSrv.update(caze, Fields(alert.toCaseJson))
                artifacts ← artifactSrv.create(caze, attrs.map(Fields.apply))
              } yield a
          })
            .map(Success(_))
            .recover { case t ⇒ Failure(t) }
      }
      .runWith(Sink.seq)
  }

  def getEventsFromDate(instanceConfig: MispInstanceConfig, fromDate: Date): Source[MispAlert, NotUsed] = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val date = dateFormat.format(fromDate)
    Source
      .fromFuture {
        ws.url(s"${instanceConfig.url}/events/index")
          .withHeaders(
            "Authorization" → instanceConfig.key,
            "Accept" → "application/json")
          .post(Json.obj("searchDatefrom" → date))
      }
      .mapConcat { response ⇒
        val eventJson = Json.parse(response.body)
          .asOpt[Seq[JsValue]]
          .getOrElse {
            logger.warn(s"Invalid MISP event format:\n${response.body}")
            Nil
          }
        val events = eventJson.flatMap { j ⇒
          j.asOpt[MispAlert]
            .map(_.copy(source = instanceConfig.name))
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
    instanceConfig: MispInstanceConfig,
    eventId: String,
    fromDate: Option[Date]): Future[Seq[JsObject]] = {
    val date = fromDate.fold("null") { fd ⇒
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
      dateFormat.format(fd)
    }
    ws
      .url(s"${instanceConfig.url}/attributes/restSearch/json/" +
        s"null/null/null/null/null/$date/null/null/$eventId/false")
      .withHeaders(
        "Authorization" → instanceConfig.key,
        "Accept" → "application/json")
      .get()
      .map { response ⇒
        val refDate = fromDate.getOrElse(new Date(0))
        val artifactTags = JsString(s"src:${instanceConfig.name}") +: JsArray(instanceConfig.artifactTags.map(JsString))
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
                  "data" → Json.obj(
                    "filename" → a.value,
                    "attributeId" → a.id,
                    "attributeType" → a.tpe).toString,
                  "startDate" → a.date))
            case a ⇒ convertAttribute(a).map { j ⇒
              val tags = artifactTags ++ (j \ "tags").asOpt[JsArray].getOrElse(JsArray(Nil))
              j.setIfAbsent("tlp", 2L) + ("tags" → tags)
            }
          }
      }
  }

  def attributeToArtifact(
    instanceConfig: MispInstanceConfig,
    alert: Alert,
    attr: JsObject)(implicit authContext: AuthContext): Option[Future[Fields]] = {
    (for {
      dataType ← (attr \ "dataType").validate[String]
      data ← (attr \ "data").validate[String]
      message ← (attr \ "message").validate[String]
      startDate ← (attr \ "startDate").validate[Date]
      attachment = dataType match {
        case "file" ⇒
          val json = Json.parse(data)
          for {
            attributeId ← (json \ "attributeId").asOpt[String]
            attributeType ← (json \ "attributeType").asOpt[String]
            fiv = downloadAttachment(instanceConfig, attributeId)
          } yield if (attributeType == "malware-sample") fiv.map(extractMalwareAttachment)
          else fiv
        case _ ⇒ None
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
        .set("tags", Json.arr(tags.filterNot(_.toLowerCase.startsWith("tlp:"))))
        .set("tlp", tlp)
    } yield attachment.fold(Future.successful(fields.set("data", data)))(_.map { fiv ⇒
      fields.set("attachment", fiv)
    })) match {
      case JsSuccess(r, _) ⇒ Some(r)
      case e: JsError ⇒
        logger.warn(s"Invalid attribute in alert ${alert.id}: $e\n$attr")
        None
    }
  }

  def createCase(alert: Alert)(implicit authContext: AuthContext): Future[Case] = {
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        for {
          instanceConfig ← getInstanceConfig(alert.source())
          caze ← caseSrv.create(Fields(alert.toCaseJson))
          _ ← alertSrv.setCase(alert, caze)
          artifacts ← Future.sequence(alert.artifacts().flatMap(attributeToArtifact(instanceConfig, alert, _)))
          _ ← artifactSrv.create(caze, artifacts)
        } yield caze
    }
  }

  //
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
        _ = zipFile.extractFile(contentFileHeader, tempFile.getParent.toString, null, tempFile.toString)
      } yield FileInputValue(filename, tempFile, "application/octet-stream")).getOrElse(file)
    }
    catch {
      case e: ZipException ⇒
        logger.warn(s"Format of malware attribute ${file.name} is invalid : zip file is unreadable", e)
        file
    }
  }

  def downloadAttachment(
    instanceConfig: MispInstanceConfig,
    attachmentId: String)(implicit authContext: AuthContext): Future[FileInputValue] = {
    val fileNameExtractor = """attachment; filename="(.*)"""".r

    ws.url(s"${instanceConfig.url}/attributes/download/$attachmentId")
      .withHeaders("Authorization" → instanceConfig.key, "Accept" → "application/json")
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
        case response ⇒ Future.successful(response)
      }
      .flatMap { response ⇒
        val tempFile = tempSrv.newTemporaryFile("misp_attachment", attachmentId)
        response.body
          .runWith(FileIO.toPath(tempFile))
          .map { ioResult ⇒
            ioResult.status.get
            // throw an exception if transfer failed
            val contentType = response.headers.headers.getOrElse("Content-Type", Seq("application/octet-stream")).head
            val filename = response.headers.headers
              .get("Content-Disposition")
              .flatMap(_.collectFirst { case fileNameExtractor(name) ⇒ name })
              .getOrElse("noname")
            FileInputValue(filename, tempFile, contentType)
          }
      }
  }

  def convertAttribute(mispAttribute: MispAttribute): Seq[JsObject] = {
    val data = mispAttribute.value
    val filenameExtractor = "filename\\|(.*)".r
    val fields = Json.obj(
      "data" → data,
      "dataType" → mispAttribute.tpe,
      "message" → mispAttribute.comment,
      "startDate" → mispAttribute.date)

    mispAttribute.tpe match {
      case filenameExtractor(hashType) ⇒
        val Array(file, hash, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" → file) + ("dataType" → JsString("filename")) + ("message" → JsString(mispAttribute.comment + s"\n$hashType:" + hash)),
          fields + ("data" → hash) + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString(hashType)))) + ("message" → JsString(mispAttribute.comment + s"\nfilename:" + file)))
      case "md5" ⇒ /* You are encouraged to use filename|md5 instead. A checksum in md5 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("md5")))))
      case "sha1" ⇒ /* You are encouraged to use filename|sha1 instead. A checksum in sha1 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha1")))))
      case "sha256" ⇒ /* You are encouraged to use filename|sha256 instead. A checksum in sha256 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha256")))))
      case "filename" ⇒ /* Filename */
        Seq(fields + ("dataType" → JsString("filename")))
      case "ip-src" ⇒ /* A source IP address of the attacker */
        Seq(fields + ("dataType" → JsString("ip")) + ("tags" → JsArray(Seq(JsString("src")))))
      case "ip-dst" ⇒ /* A destination IP address of the attacker or C&C server. Also set the IDS flag on when this IP is hardcoded in malware */
        Seq(fields + ("dataType" → JsString("ip")) + ("tags" → JsArray(Seq(JsString("dst")))))
      case "hostname" ⇒ /* A full host/dnsname of an attacker. Also set the IDS flag on when this hostname is hardcoded in malware */
        Seq(fields + ("dataType" → JsString("fqdn")))
      case "domain" ⇒ /* A domain name used in the malware. Use this instead of hostname when the upper domain is important or can be used to create links between events. */
        Seq(fields + ("dataType" → JsString("domain")))
      case "domain|ip" ⇒ /* A domain name and its IP address (as found in DNS lookup) separated by a | (no spaces) */
        val Array(domain, ip, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" → domain) + ("dataType" → JsString("domain")) + ("message" → JsString(mispAttribute.comment + "\nip:" + ip)),
          fields + ("data" → ip) + ("dataType" → JsString("ip")) + ("message" → JsString(mispAttribute.comment + "\ndomain:" + domain)))
      case "email-src" ⇒ /* The email address (or domainname) used to send the malware. */
        Seq(fields + ("dataType" → JsString("mail")) + ("tags" → JsArray(Seq(JsString("src")))))
      case "email-dst" ⇒ /* A recipient email address that is not related to your constituency. */
        Seq(fields + ("dataType" → JsString("mail")) + ("tags" → JsArray(Seq(JsString("dst")))))
      case "email-subject" ⇒ /* The subject of the email */
        Seq(fields + ("dataType" → JsString("mail_subject")))
      case "email-attachment" ⇒ /* File name of the email attachment. */
        Seq(fields + ("dataType" → JsString("filename")) + ("tags" → JsArray(Seq(JsString("mail")))))
      case "url" ⇒ /* url */
        Seq(fields + ("dataType" → JsString("url")))
      case "http-method" ⇒ /* HTTP method used by the malware (e.g. POST, GET, ...). */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("http-method")))))
      case "user-agent" ⇒ /* The user-agent used by the malware in the HTTP request. */
        Seq(fields + ("dataType" → JsString("user-agent")))
      case "regkey" ⇒ /* Registry key or value */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("regkey")))))
      case "regkey|value" ⇒ /* Registry value + data separated by | */
        val Array(regkey, value, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" → regkey) + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("regkey")))) + ("message" → JsString(mispAttribute.comment + "\nvalue:" + value)),
          fields + ("data" → value) + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("regkey-value")))) + ("message" → JsString(mispAttribute.comment + "\nkey:" + regkey)))
      case "AS" ⇒ /* Autonomous system */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("AS")))))
      case "snort" ⇒ /* An IDS rule in Snort rule-format. This rule will be automatically rewritten in the NIDS exports. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("snort")))))
      case "pattern-in-file" ⇒ /* Pattern in file that identifies the malware */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("pattern-in-file")))))
      case "pattern-in-traffic" ⇒ /* Pattern in network traffic that identifies the malware */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("pattern-in-traffic")))))
      case "pattern-in-memory" ⇒ /* Pattern in memory dump that identifies the malware */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("pattern-int-memory")))))
      case "yara" ⇒ /* Yara signature */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("yara")))))
      case "vulnerability" ⇒ /* A reference to the vulnerability used in the exploit */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("vulnerability")))))
      case "attachment" ⇒ /* Please upload files using the Upload Attachment button. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("attachment")))))
      case "malware-sample" ⇒ /* Please upload files using the Upload Attachment button. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("malware-sample")))))
      case "link" ⇒ /* Link to an external information */
        Nil // Don't import link as observable // Seq(fields + ("dataType" -> JsString("url")))
      case "comment" ⇒ /* Comment or description in a human language. This will not be correlated with other attributes */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("comment")))))
      case "text" ⇒ /* Name, ID or a reference */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("text")))))
      case "other" ⇒ /* Other attribute */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("other")))))
      case "named pipe" ⇒ /* Named pipe, use the format \.\pipe\      */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("named pipe")))))
      case "mutex" ⇒ /* Mutex, use the format \BaseNamedObjects\      */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("mutex")))))
      case "target-user" ⇒ /* Attack Targets Username(s) */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("target-user")))))
      case "target-email" ⇒ /* Attack Targets Email(s) */
        Seq(fields + ("dataType" → JsString("mail")) + ("tags" → JsArray(Seq(JsString("target-email")))))
      case "target-machine" ⇒ /* Attack Targets Machine Name(s) */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("target-machine")))))
      case "target-org" ⇒ /* Attack Targets Department or Orginization(s) */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("target-org")))))
      case "target-location" ⇒ /* Attack Targets Physical Location(s) */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("target-location")))))
      case "target-external" ⇒ /* External Target Orginizations Affected by this Attack */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("target-external")))))
      case "btc" ⇒ /* Bitcoin Address */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("btc")))))
      case "iban" ⇒ /* International Bank Account Number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("iban")))))
      case "bic" ⇒ /* Bank Identifier Code Number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("bic")))))
      case "bank-account-nr" ⇒ /* Bank account number without any routing number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("bank-account-nr")))))
      case "aba-rtn" ⇒ /* ABA routing transit number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("aba-rtn")))))
      case "bin" ⇒ /* Bank Identification Number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("bin")))))
      case "cc-number" ⇒ /* Credit-Card Number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("cc-number")))))
      case "prtn" ⇒ /* Premium-Rate Telephone Number */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("prtn")))))
      case "threat-actor" ⇒ /* A string identifying the threat actor */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("threat-actor")))))
      case "campaign-name" ⇒ /* Associated campaign name */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("campaign-name")))))
      case "campaign-id" ⇒ /* Associated campaign ID */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("campaign-id")))))
      case "malware-type" ⇒ /*  */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("malware-type")))))
      case "uri" ⇒ /* Uniform Resource Identifier */
        Seq(fields + ("dataType" → JsString("url")))
      case "authentihash" ⇒ /* You are encouraged to use filename|authentihash instead. Authenticode executable signature hash, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("authentihash")))))
      case "ssdeep" ⇒ /* You are encouraged to use filename|ssdeep instead. A checksum in the SSDeep format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("ssdeep")))))
      case "imphash" ⇒ /* You are encouraged to use filename|imphash instead. A hash created based on the imports in the sample, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("imphash")))))
      case "pehash" ⇒ /* PEhash - a hash calculated based of certain pieces of a PE executable file */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("pehash")))))
      case "sha224" ⇒ /* You are encouraged to use filename|sha224 instead. A checksum in sha224 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha-224")))))
      case "sha384" ⇒ /* You are encouraged to use filename|sha384 instead. A checksum in sha384 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha-384")))))
      case "sha512" ⇒ /* You are encouraged to use filename|sha512 instead. A checksum in sha512 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha-512")))))
      case "sha512/224" ⇒ /* You are encouraged to use filename|sha512/224 instead. A checksum in sha512/224 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha-512/224")))))
      case "sha512/256" ⇒ /* You are encouraged to use filename|sha512/256 instead. A checksum in sha512/256 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("sha-512/256")))))
      case "tlsh" ⇒ /* You are encouraged to use filename|tlsh instead. A checksum in the Trend Micro Locality Sensitive Hash format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" → JsString("hash")) + ("tags" → JsArray(Seq(JsString("tlsh")))))
      case "windows-scheduled-task" ⇒ /* A scheduled task in windows */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("windows-scheduled-task")))))
      case "windows-service-name" ⇒ /* A windows service name. This is the name used internally by windows. Not to be confused with the windows-service-displayname. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("windows-service-name")))))
      case "windows-service-displayname" ⇒ /* A windows service's displayname, not to be confused with the windows-service-name. This is the name that applications will generally display as the service's name in applications. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("windows-service-displayname")))))
      case "whois-registrant-email" ⇒ /* The e-mail of a domain's registrant, obtained from the WHOIS information. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("whois-registrant-email")))))
      case "whois-registrant-phone" ⇒ /* The phone number of a domain's registrant, obtained from the WHOIS information. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("whois-registrant-phone")))))
      case "whois-registar" ⇒ /* The registar of the domain, obtained from the WHOIS information. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("whois-registar")))))
      case "whois-creation-date" ⇒ /* The date of domain's creation, obtained from the WHOIS information. */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("whois-creation-date")))))
      case "targeted-threat-index" ⇒ /*  */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("targeted-threat-index")))))
      case "mailslot" ⇒ /* MailSlot interprocess communication */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("mailslot")))))
      case "pipe" ⇒ /* Pipeline (for named pipes use the attribute type "named pipe") */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("pipe")))))
      case "ssl-cert-attributes" ⇒ /* SSL certificate attributes  */
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString("ssl-cert-attributes")))))
      case other ⇒ /* unknown attribute type */
        logger.warn(s"Unknown attribute type : $other")
        Seq(fields + ("dataType" → JsString("other")) + ("tags" → JsArray(Seq(JsString(other)))))
    }
  }
}
