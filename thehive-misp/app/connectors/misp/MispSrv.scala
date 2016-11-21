package connectors.misp

import java.nio.file.{ Path, Paths }

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ DurationInt, DurationLong, FiniteDuration }
import scala.math.BigDecimal.long2bigDecimal
import scala.util.{ Failure, Left, Right, Success, Try }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ FileIO, Sink, Source }

import play.api.{ Configuration, Environment, Logger }
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{ JsArray, JsBoolean }
import play.api.libs.json.{ JsNull, JsNumber, JsObject, JsString, JsValue }
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.ws.WSClientConfig
import play.api.libs.ws.ahc.{ AhcWSAPI, AhcWSClientConfig }
import play.api.libs.ws.ssl.{ SSLConfig, TrustManagerConfig, TrustStoreConfig }

import org.elastic4play.{ InternalError, NotFoundError }
import org.elastic4play.controllers.{ Fields, FileInputValue }
import org.elastic4play.services.{ Agg, AttachmentSrv, AuthContext, CreateSrv, FindSrv, GetSrv, QueryDef, TempSrv, UpdateSrv }
import org.elastic4play.utils.RichJson

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader

import JsonFormat.{ attributeReads, eventReads, eventStatusFormat, eventWrites }
import models.{ Artifact, Case, CaseModel, CaseStatus, CaseResolutionStatus }
import services.{ ArtifactSrv, CaseSrv, CaseTemplateSrv, UserSrv }

case class MispInstanceConfig(name: String, url: String, key: String, caseTemplate: Option[String], artifactTags: Seq[String])
object MispInstanceConfig {
  def apply(name: String, defaultCaseTemplate: Option[String], configuration: Configuration): Option[MispInstanceConfig] =
    for {
      url <- configuration.getString("url")
      key <- configuration.getString("key")
      tags = configuration.getStringSeq("tags").getOrElse(Nil)
    } yield MispInstanceConfig(name, url, key, configuration.getString("caseTemplate") orElse defaultCaseTemplate, tags)
}
case class MispConfig(truststore: Option[Path], interval: FiniteDuration, instances: Seq[MispInstanceConfig]) {

  def this(configuration: Configuration, defaultCaseTemplate: Option[String]) = this(
    configuration.getString("misp.cert").map(p => Paths.get(p)),
    configuration.getMilliseconds("misp.interval").map(_.millis).getOrElse(1.hour),
    for {
      cfg <- configuration.getConfig("misp").toSeq
      key <- cfg.subKeys
      c <- Try(cfg.getConfig(key)).toOption.flatten.toSeq
      mic <- MispInstanceConfig(key, defaultCaseTemplate, c)
    } yield mic)

  @Inject def this(configuration: Configuration) =
    this(configuration, configuration.getString("misp.caseTemplate"))
}

@Singleton
class MispSrv @Inject() (mispConfig: MispConfig,
                         mispModel: MispModel,
                         caseSrv: CaseSrv,
                         artifactSrv: ArtifactSrv,
                         createSrv: CreateSrv,
                         getSrv: GetSrv,
                         findSrv: FindSrv,
                         updateSrv: UpdateSrv,
                         userSrv: UserSrv,
                         caseTemplateSrv: CaseTemplateSrv,
                         attachmentSrv: AttachmentSrv,
                         caseModel: CaseModel,
                         tempSrv: TempSrv,
                         environment: Environment,
                         lifecycle: ApplicationLifecycle,
                         system: ActorSystem,
                         implicit val materializer: Materializer,
                         implicit val ec: ExecutionContext) {

  val log = Logger(getClass)
  val ws = {
    val config = mispConfig.truststore match {
      case Some(p) => AhcWSClientConfig(wsClientConfig = WSClientConfig(ssl = SSLConfig(trustManagerConfig = TrustManagerConfig(trustStoreConfigs = Seq(TrustStoreConfig(filePath = Some(p.toString), data = None))))))
      case None    => AhcWSClientConfig()
    }
    new AhcWSAPI(environment, config, lifecycle)
  }

  private[misp] def getInstanceConfig(name: String): Future[MispInstanceConfig] = mispConfig.instances
    .find(_.name == name)
    .fold[Future[MispInstanceConfig]](Future.failed(NotFoundError(s"""Configuration of MISP server "${name}" not found"""))) { instanceConfig =>
      Future.successful(instanceConfig)
    }

  def initScheduler() = {
    val task = system.scheduler.schedule(0.seconds, mispConfig.interval) {
      log.info("Update of MISP events is starting ...")
      userSrv
        .inInitAuthContext { implicit authContext =>
          update().andThen { case _ => tempSrv.releaseTemporaryFiles() }
        }
        .onComplete {
          case Success(misps) =>
            val successMisp = misps.flatMap {
              case Success(m) => Seq(m)
              case Failure(t) => log.warn("Misp import fail", t); Nil
            }
            log.info(s"${successMisp.size} MISP event(s) updated")
          case Failure(error) => log.error("Update MISP events error :", error)
        }
      ()
    }
    lifecycle.addStopHook { () =>
      log.info("Stopping MISP fetching ...")
      task.cancel()
      Future.successful(())
    }
  }

  initScheduler()

  /**
   * Get list of all MISP event (without attributes)
   */
  def getEvents: Future[Seq[MispEvent]] = {
    Future
      .sequence(mispConfig.instances.map { c =>
        getEvents(c).recoverWith {
          case t =>
            log.warn("Retrieve MISP event list failed", t)
            Future.failed(t)
        }
      })
      .map(_.flatten)
  }

  def getEvents(instanceConfig: MispInstanceConfig): Future[Seq[MispEvent]] = {
    ws.url(s"${instanceConfig.url}/events/index")
      .withHeaders("Authorization" -> instanceConfig.key, "Accept" -> "application/json")
      .get()
      .map { response =>
        val eventJson = Json.parse(response.body)
          .asOpt[Seq[JsValue]]
          .getOrElse(Nil)
        val events = eventJson.flatMap { j =>
          j.asOpt[MispEvent].map(_.copy(serverId = instanceConfig.name)) orElse {
            log.warn(s"MISP event can't be parsed\n$j")
            None
          }
        }
        val eventJsonSize = eventJson.size
        val eventsSize = events.size
        if (eventJsonSize != eventsSize)
          log.warn(s"MISP returns $eventJsonSize events but only $eventsSize contain valid data")
        events
      }
  }

  def extractMalwareAttachment(file: FileInputValue)(implicit authContext: AuthContext): FileInputValue = {
    import scala.collection.JavaConversions._
    try {
      val zipFile = new ZipFile(file.filepath.toFile)

      if (zipFile.isEncrypted)
        zipFile.setPassword("infected");

      // Get the list of file headers from the zip file
      val fileHeaders = zipFile.getFileHeaders.toList.asInstanceOf[List[FileHeader]]
      val (fileNameHeaders, contentFileHeaders) = fileHeaders.partition {
        case fileHeader: FileHeader => fileHeader.getFileName.endsWith(".filename.txt")
      }
      (for {
        fileNameHeader <- fileNameHeaders
          .headOption
          .orElse { log.warn(s"Format of malware attribute ${file.name} is invalid : file containing filename not found"); None }
        buffer = Array.ofDim[Byte](128)
        len = zipFile.getInputStream(fileNameHeader).read(buffer)
        filename = new String(buffer, 0, len)

        contentFileHeader <- contentFileHeaders
          .headOption
          .orElse { log.warn(s"Format of malware attribute ${file.name} is invalid : content file not found"); None }

        tempFile = tempSrv.newTemporaryFile("misp_malware", file.name)
        _ = log.info(s"Extract malware file ${file.filepath} in file $tempFile")
        _ = zipFile.extractFile(contentFileHeader, tempFile.getParent.toString, null, tempFile.toString)
      } yield FileInputValue(filename, tempFile, "application/octet-stream")).getOrElse(file)
    } catch {
      case e: ZipException =>
        log.warn(s"Format of malware attribute ${file.name} is invalid : zip file is unreadable", e)
        file
    }
  }

  def downloadAttachment(instanceConfig: MispInstanceConfig, attachmentId: String)(implicit authContext: AuthContext): Future[FileInputValue] = {
    val fileNameExtractor = """attachment; filename="(.*)"""".r

    ws.url(s"${instanceConfig.url}/attributes/download/$attachmentId")
      .withHeaders("Authorization" -> instanceConfig.key, "Accept" -> "application/json")
      .withMethod("GET")
      .stream()
      .flatMap {
        case response if response.headers.status != 200 =>
          val status = response.headers.status
          response.body.runWith(Sink.headOption).flatMap { body =>
            val message = body.fold("<no body>")(_.decodeString("UTF-8"))
            log.warn(s"MISP attachment $attachmentId can't be downloaded (status $status) : $message")
            Future.failed(InternalError(s"MISP attachment $attachmentId can't be downloaded (status $status)"))
          }
        case response => Future.successful(response)
      }
      .flatMap { response =>
        val tempFile = tempSrv.newTemporaryFile("misp_attachment", attachmentId)
        response.body
          .runWith(FileIO.toPath(tempFile))
          .map { ioResult =>
            ioResult.status.get // throw an exception if transfer failed
            val contentType = response.headers.headers.getOrElse("Content-Type", Seq("application/octet-stream")).head
            val filename = response.headers.headers
              .get("Content-Disposition")
              .flatMap(_.collectFirst { case fileNameExtractor(name) => name })
              .getOrElse("noname")
            FileInputValue(filename, tempFile, contentType)
          }
      }
  }

  def update()(implicit authContext: AuthContext): Future[Seq[Try[Misp]]] = {
      def getEventFields(event: MispEvent) = {
        val fields = Fields(eventWrites.writes(event))
        fields.getLong("threatLevel").fold(fields)(threatLevel => fields.set("threatLevel", JsNumber(4 - threatLevel)))
      }

      def flatFutureSeq[A](s: Seq[Future[Seq[A]]]): Future[Seq[A]] = Future.sequence(s).map(_.flatten) // Futures can't fail, they are recovered

      def getAndSortMispFromEvents(events: Seq[MispEvent]): Future[Seq[Either[(Misp, Fields), Fields]]] = flatFutureSeq(
        events.map { event =>
          val fields = getEventFields(event)
          getMisp(event._id)
            .map {
              case misp if (misp.publishDate() before event.publishDate) =>
                misp.eventStatus() match {
                  case EventStatus.Ignore | EventStatus.Imported if misp.follow() => Seq(Left(misp -> fields.set("eventStatus", "Update").unset("_id")))
                  case _                                                          => Seq(Left(misp -> fields.unset("_id")))
                }
              case _ => Seq.empty
            }
            .recover {
              case _: NotFoundError => Seq(Right(fields))
              case error            => log.error("MISP error", error); Seq.empty
            }
        })

      def partitionEither[L, R](eithers: Seq[Either[L, R]]) = {
        eithers.foldLeft((Seq.empty[L], Seq.empty[R])) {
          case ((ls, rs), Left(l))  => (l +: ls, rs)
          case ((ls, rs), Right(r)) => (ls, r +: rs)
        }
      }

    def getSuccessMispAndCase(misp: Seq[Try[Misp]]): Future[Seq[(Misp, Case)]] = {
      val successMisp = misp.collect {
        case Success(m) => m
      }
      Future
        .traverse(successMisp) { misp =>
          caseSrv.get(misp.id).map(misp -> _)
        }
        // remove deleted and merged cases
        .map {
          _.filter {
            case (misp, caze) => caze.status() != CaseStatus.Deleted && caze.resolutionStatus != CaseResolutionStatus.Duplicated
          }
        }
    }

    /* for all misp servers, retrieve events */
    getEvents
      /* sort events into : case must be updated (Left) and case must be created (Right) */
      .flatMap(getAndSortMispFromEvents)
      .map(partitionEither)
      .flatMap {
        case (updates, creates) =>
          for {
            updatedMisp <- updateSrv(updates)
            createdMisp <- createSrv[MispModel, Misp](mispModel, creates)
            misp = updatedMisp ++ createdMisp
            importedMisp <- getSuccessMispAndCase(updatedMisp)
            // update case status
            _ <- caseSrv.bulkUpdate(importedMisp.map(_._2.id), Fields.empty.set("status", CaseStatus.Open.toString))
            // and import MISP attributes
            _  ← Future.traverse(importedMisp) {
              case (m, c) =>
                importAttributes(m, c).fallbackTo(Future.successful(Nil))
            }
          } yield misp
      }
  }

  def pullEvent(instanceConfig: MispInstanceConfig, eventId: Long): Future[JsValue] = {
    ws
      .url(s"${instanceConfig.url}/events/$eventId")
      .withHeaders(("Authorization" -> instanceConfig.key), ("Accept" -> "application/json"))
      .get()
      .map { response =>
        Json.parse(response.body)
      }
  }

  def createCase(mispId: String)(implicit authContext: AuthContext): Future[(Case, Seq[Try[Artifact]])] =
    getMisp(mispId).flatMap(misp => createCase(misp))

  def createCase(misp: Misp)(implicit authContext: AuthContext): Future[(Case, Seq[Try[Artifact]])] = {
    misp.caze() match {
      case Some(id) => caseSrv.get(id).map(_ -> Nil)
      case None =>
        for {
          instanceConfig <- getInstanceConfig(misp.serverId())
          caseTemplate <- instanceConfig.caseTemplate match {
            case Some(templateName) => caseTemplateSrv.getByName(templateName).map(ct => Some(ct)).recover { case _ => None }
            case None               => Future.successful(None)
          }
          attributes <- getAttributes(misp)
          links = attributes
            .collect { case a if a.tpe == "link" => a.value }
          linkDescription = if (links.isEmpty) "" else
            links.mkString("\n\nLinks attributes :\n\n - ", "\n - ", "")
          caseTitle = (caseTemplate.flatMap(_.titlePrefix()).getOrElse("") + s" #${misp.eventId()} " + misp.info()).trim
          caseTlp = misp.tags()
            .map(_.toLowerCase)
            .collectFirst {
              case "tlp:white" => 0L
              case "tlp:green" => 1L
              case "tlp:amber" => 2L
              case "tlp:red"   => 3L
            }
            .orElse(caseTemplate.flatMap(_.tlp()))
            .getOrElse(2L)
          caseTags = misp.tags().filterNot(_.toLowerCase.startsWith("tlp:")) ++ caseTemplate.fold[Seq[String]](Nil)(_.tags()) :+ s"src:${misp.org()}"
          caseFlag = caseTemplate.flatMap(_.flag()).getOrElse(false)
          caseMetrics = caseTemplate.fold(JsObject(Nil))(ct => JsObject(ct.metricNames().map(_ -> JsNull)))
          caseTasks = caseTemplate.fold[Seq[JsObject]](Nil)(_.tasks())
          caseFields = Json.obj(
            "title" -> caseTitle,
            "description" -> s"Imported from MISP Event #${misp.eventId()}, created at ${misp.date()}${linkDescription}",
            "tlp" -> caseTlp,
            "severity" -> misp.threatLevel(),
            "flag" -> caseFlag,
            "tags" -> caseTags,
            "metrics" -> caseMetrics,
            "tasks" -> caseTasks)
          caze <- caseSrv.create(Fields(caseFields))
          _ <- setCaseId(misp.id, caze.id)
          artifacts <- importAttributes(misp, caze)
        } yield (caze, artifacts)
    }
  }

  def getMisp(mispId: String): Future[Misp] =
    getSrv[MispModel, Misp](mispModel, mispId)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Misp, NotUsed], Future[Long]) = {
    import org.elastic4play.services.QueryDSL._
    findSrv[MispModel, Misp](mispModel, and(queryDef, not("eventStatus" in ("Ignore", "Imported"))), range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]) = findSrv(mispModel, queryDef, aggs: _*)

  def ignoreEvent(mispId: String)(implicit authContext: AuthContext) = {
    updateSrv[MispModel, Misp](mispModel, mispId, Fields(JsObject(Seq("eventStatus" -> JsString("Ignore")))))
  }

  def setFollowEvent(mispId: String, follow: Boolean)(implicit authContext: AuthContext) = {
    updateSrv[MispModel, Misp](mispModel, mispId, Fields(JsObject(Seq("follow" -> JsBoolean(follow)))))
  }

  def setCaseId(mispId: String, caseId: String)(implicit authContext: AuthContext) = {
    updateSrv[MispModel, Misp](mispModel, mispId, Fields(Json.obj("case" -> caseId, "eventStatus" -> EventStatus.Imported)))
  }

  def importAttributes(mispId: String)(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] = {
    getSrv[MispModel, Misp](mispModel, mispId)
      .flatMap { misp => importAttributes(misp) }
  }

  def importAttributes(misp: Misp)(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] = {
    for {
      caseId <- misp.caze() match {
        case Some(c) => Future.successful(c)
        case None    => Future.failed(NotFoundError(s"No case defined for MISP event ${misp.id}"))
      }
      caze <- getSrv[CaseModel, Case](caseModel, caseId)
      artifacts <- importAttributes(misp, caze)
    } yield artifacts
  }

  def importAttributes(misp: Misp, caze: Case)(implicit authContext: AuthContext): Future[Seq[Try[Artifact]]] = {
    for {
      instanceConfig <- getInstanceConfig(misp.serverId())
      mispAttributes <- getAttributes(misp)
      artifactTags = JsString(s"src:${misp.org()}") +: JsArray(instanceConfig.artifactTags.map(JsString))
      attributes <- Future.sequence(mispAttributes
        .map {
          case a if a.tpe == "attachment" =>
            downloadAttachment(instanceConfig, a.id)
              .map { attachment =>
                Seq(Fields.empty
                  .set("dataType", "file")
                  .set("message", a.comment)
                  .set("tags", artifactTags)
                  .set("attachment", attachment))
              }
              .recover { case _ => Nil }
          case a if a.tpe == "malware-sample" =>
            downloadAttachment(instanceConfig, a.id)
              .map { attachment =>
                Seq(Fields.empty
                  .set("dataType", "file")
                  .set("message", a.comment)
                  .set("tags", artifactTags)
                  .set("attachment", extractMalwareAttachment(attachment)))
              }
              .recover { case _ => Nil }
          case a => Future.successful(convertAttribute(a).map { a =>
            val tags = artifactTags ++ (a \ "tags").asOpt[JsArray].getOrElse(JsArray(Nil))
            Fields(a.setIfAbsent("tlp", caze.tlp()) + ("tags" -> tags))
          })
        })
      artifactFields = attributes.flatten
      artifacts <- artifactSrv.create(caze, artifactFields)
    } yield artifacts
  }

  def getAttributes(misp: Misp): Future[Seq[MispAttribute]] = {
    for {
      instanceConfig <- getInstanceConfig(misp.serverId())
      event <- pullEvent(instanceConfig, misp.eventId())
    } yield (event \ "Event" \ "Attribute").as[Seq[MispAttribute]]
  }

  def convertAttribute(mispAttribute: MispAttribute)(implicit authContext: AuthContext): Seq[JsObject] = {
    val data = mispAttribute.value
    val filenameExtractor = "filename\\|(.*)".r
    val fields = JsObject(Seq(
      "data" -> JsString(data),
      "dataType" -> JsString(mispAttribute.tpe),
      "message" -> JsString(mispAttribute.comment)))
    mispAttribute.tpe match {
      case filenameExtractor(hashType) =>
        val Array(file, hash, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" -> file) + ("dataType" -> JsString("filename")) + ("message" -> JsString(mispAttribute.comment + s"\n$hashType:" + hash)),
          fields + ("data" -> hash) + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString(hashType)))) + ("message" -> JsString(mispAttribute.comment + s"\nfilename:" + file)))
      case "md5" => /* You are encouraged to use filename|md5 instead. A checksum in md5 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("md5")))))
      case "sha1" => /* You are encouraged to use filename|sha1 instead. A checksum in sha1 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha1")))))
      case "sha256" => /* You are encouraged to use filename|sha256 instead. A checksum in sha256 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha256")))))
      case "filename" => /* Filename */
        Seq(fields + ("dataType" -> JsString("filename")))
      case "ip-src" => /* A source IP address of the attacker */
        Seq(fields + ("dataType" -> JsString("ip")) + ("tags" -> JsArray(Seq(JsString("src")))))
      case "ip-dst" => /* A destination IP address of the attacker or C&C server. Also set the IDS flag on when this IP is hardcoded in malware */
        Seq(fields + ("dataType" -> JsString("ip")) + ("tags" -> JsArray(Seq(JsString("dst")))))
      case "hostname" => /* A full host/dnsname of an attacker. Also set the IDS flag on when this hostname is hardcoded in malware */
        Seq(fields + ("dataType" -> JsString("fqdn")))
      case "domain" => /* A domain name used in the malware. Use this instead of hostname when the upper domain is important or can be used to create links between events. */
        Seq(fields + ("dataType" -> JsString("domain")))
      case "domain|ip" => /* A domain name and its IP address (as found in DNS lookup) separated by a | (no spaces) */
        val Array(domain, ip, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" -> domain) + ("dataType" -> JsString("domain")) + ("message" -> JsString(mispAttribute.comment + "\nip:" + ip)),
          fields + ("data" -> ip) + ("dataType" -> JsString("ip")) + ("message" -> JsString(mispAttribute.comment + "\ndomain:" + domain)))
      case "email-src" => /* The email address (or domainname) used to send the malware. */
        Seq(fields + ("dataType" -> JsString("mail")) + ("tags" -> JsArray(Seq(JsString("src")))))
      case "email-dst" => /* A recipient email address that is not related to your constituency. */
        Seq(fields + ("dataType" -> JsString("mail")) + ("tags" -> JsArray(Seq(JsString("dst")))))
      case "email-subject" => /* The subject of the email */
        Seq(fields + ("dataType" -> JsString("mail_subject")))
      case "email-attachment" => /* File name of the email attachment. */
        Seq(fields + ("dataType" -> JsString("filename")) + ("tags" -> JsArray(Seq(JsString("mail")))))
      case "url" => /* url */
        Seq(fields + ("dataType" -> JsString("url")))
      case "http-method" => /* HTTP method used by the malware (e.g. POST, GET, ...). */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("http-method")))))
      case "user-agent" => /* The user-agent used by the malware in the HTTP request. */
        Seq(fields + ("dataType" -> JsString("user-agent")))
      case "regkey" => /* Registry key or value */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("regkey")))))
      case "regkey|value" => /* Registry value + data separated by | */
        val Array(regkey, value, _*) = (data + "|").split("\\|", 3).map(JsString)
        Seq(
          fields + ("data" -> regkey) + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("regkey")))) + ("message" -> JsString(mispAttribute.comment + "\nvalue:" + value)),
          fields + ("data" -> value) + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("regkey-value")))) + ("message" -> JsString(mispAttribute.comment + "\nkey:" + regkey)))
      case "AS" => /* Autonomous system */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("AS")))))
      case "snort" => /* An IDS rule in Snort rule-format. This rule will be automatically rewritten in the NIDS exports. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("snort")))))
      case "pattern-in-file" => /* Pattern in file that identifies the malware */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("pattern-in-file")))))
      case "pattern-in-traffic" => /* Pattern in network traffic that identifies the malware */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("pattern-in-traffic")))))
      case "pattern-in-memory" => /* Pattern in memory dump that identifies the malware */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("pattern-int-memory")))))
      case "yara" => /* Yara signature */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("yara")))))
      case "vulnerability" => /* A reference to the vulnerability used in the exploit */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("vulnerability")))))
      case "attachment" => /* Please upload files using the Upload Attachment button. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("attachment")))))
      case "malware-sample" => /* Please upload files using the Upload Attachment button. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("malware-sample")))))
      case "link" => /* Link to an external information */
        Nil // Don't import link as observable // Seq(fields + ("dataType" -> JsString("url")))
      case "comment" => /* Comment or description in a human language. This will not be correlated with other attributes */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("comment")))))
      case "text" => /* Name, ID or a reference */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("text")))))
      case "other" => /* Other attribute */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("other")))))
      case "named pipe" => /* Named pipe, use the format \.\pipe\      */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("named pipe")))))
      case "mutex" => /* Mutex, use the format \BaseNamedObjects\      */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("mutex")))))
      case "target-user" => /* Attack Targets Username(s) */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("target-user")))))
      case "target-email" => /* Attack Targets Email(s) */
        Seq(fields + ("dataType" -> JsString("mail")) + ("tags" -> JsArray(Seq(JsString("target-email")))))
      case "target-machine" => /* Attack Targets Machine Name(s) */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("target-machine")))))
      case "target-org" => /* Attack Targets Department or Orginization(s) */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("target-org")))))
      case "target-location" => /* Attack Targets Physical Location(s) */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("target-location")))))
      case "target-external" => /* External Target Orginizations Affected by this Attack */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("target-external")))))
      case "btc" => /* Bitcoin Address */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("btc")))))
      case "iban" => /* International Bank Account Number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("iban")))))
      case "bic" => /* Bank Identifier Code Number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("bic")))))
      case "bank-account-nr" => /* Bank account number without any routing number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("bank-account-nr")))))
      case "aba-rtn" => /* ABA routing transit number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("aba-rtn")))))
      case "bin" => /* Bank Identification Number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("bin")))))
      case "cc-number" => /* Credit-Card Number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("cc-number")))))
      case "prtn" => /* Premium-Rate Telephone Number */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("prtn")))))
      case "threat-actor" => /* A string identifying the threat actor */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("threat-actor")))))
      case "campaign-name" => /* Associated campaign name */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("campaign-name")))))
      case "campaign-id" => /* Associated campaign ID */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("campaign-id")))))
      case "malware-type" => /*  */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("malware-type")))))
      case "uri" => /* Uniform Resource Identifier */
        Seq(fields + ("dataType" -> JsString("url")))
      case "authentihash" => /* You are encouraged to use filename|authentihash instead. Authenticode executable signature hash, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("authentihash")))))
      case "ssdeep" => /* You are encouraged to use filename|ssdeep instead. A checksum in the SSDeep format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("ssdeep")))))
      case "imphash" => /* You are encouraged to use filename|imphash instead. A hash created based on the imports in the sample, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("imphash")))))
      case "pehash" => /* PEhash - a hash calculated based of certain pieces of a PE executable file */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("pehash")))))
      case "sha224" => /* You are encouraged to use filename|sha224 instead. A checksum in sha224 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha-224")))))
      case "sha384" => /* You are encouraged to use filename|sha384 instead. A checksum in sha384 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha-384")))))
      case "sha512" => /* You are encouraged to use filename|sha512 instead. A checksum in sha512 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha-512")))))
      case "sha512/224" => /* You are encouraged to use filename|sha512/224 instead. A checksum in sha512/224 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha-512/224")))))
      case "sha512/256" => /* You are encouraged to use filename|sha512/256 instead. A checksum in sha512/256 format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("sha-512/256")))))
      case "tlsh" => /* You are encouraged to use filename|tlsh instead. A checksum in the Trend Micro Locality Sensitive Hash format, only use this if you don't know the correct filename */
        Seq(fields + ("dataType" -> JsString("hash")) + ("tags" -> JsArray(Seq(JsString("tlsh")))))
      case "windows-scheduled-task" => /* A scheduled task in windows */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("windows-scheduled-task")))))
      case "windows-service-name" => /* A windows service name. This is the name used internally by windows. Not to be confused with the windows-service-displayname. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("windows-service-name")))))
      case "windows-service-displayname" => /* A windows service's displayname, not to be confused with the windows-service-name. This is the name that applications will generally display as the service's name in applications. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("windows-service-displayname")))))
      case "whois-registrant-email" => /* The e-mail of a domain's registrant, obtained from the WHOIS information. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("whois-registrant-email")))))
      case "whois-registrant-phone" => /* The phone number of a domain's registrant, obtained from the WHOIS information. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("whois-registrant-phone")))))
      case "whois-registar" => /* The registar of the domain, obtained from the WHOIS information. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("whois-registar")))))
      case "whois-creation-date" => /* The date of domain's creation, obtained from the WHOIS information. */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("whois-creation-date")))))
      case "targeted-threat-index" => /*  */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("targeted-threat-index")))))
      case "mailslot" => /* MailSlot interprocess communication */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("mailslot")))))
      case "pipe" => /* Pipeline (for named pipes use the attribute type "named pipe") */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("pipe")))))
      case "ssl-cert-attributes" => /* SSL certificate attributes  */
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString("ssl-cert-attributes")))))
      case other => /* unknown attribute type */
        log.warn(s"Unknown attribute type : $other")
        Seq(fields + ("dataType" -> JsString("other")) + ("tags" -> JsArray(Seq(JsString(other)))))
    }
  }
}
