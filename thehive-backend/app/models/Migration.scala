package models

import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.collection.immutable.{ Set ⇒ ISet }
import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Try

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._
import play.api.{ Configuration, Logger }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import services.AlertSrv

import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services._
import org.elastic4play.utils
import org.elastic4play.utils.{ Hasher, RichJson }

case class UpdateMispAlertArtifact() extends EventMessage

@Singleton
class Migration(
    mispCaseTemplate: Option[String],
    mainHash: String,
    extraHashes: Seq[String],
    datastoreName: String,
    models: ISet[BaseModelDef],
    dblists: DBLists,
    eventSrv: EventSrv,
    implicit val ec: ExecutionContext,
    implicit val materializer: Materializer) extends MigrationOperations {
  @Inject() def this(
      configuration: Configuration,
      models: ISet[BaseModelDef],
      dblists: DBLists,
      eventSrv: EventSrv,
      ec: ExecutionContext,
      materializer: Materializer) = {
    this(
      configuration.getOptional[String]("misp.caseTemplate"),
      configuration.get[String]("datastore.hash.main"),
      configuration.get[Seq[String]]("datastore.hash.extra"),
      configuration.get[String]("datastore.name"),
      models, dblists,
      eventSrv, ec, materializer)
  }

  import org.elastic4play.services.Operation._

  private[Migration] lazy val logger = Logger(getClass)
  private var requireUpdateMispAlertArtifact = false

  override def beginMigration(version: Int): Future[Unit] = Future.successful(())

  override def endMigration(version: Int): Future[Unit] = {
    if (requireUpdateMispAlertArtifact) {
      logger.info("Retrieve MISP attribute to update alerts")
      eventSrv.publish(UpdateMispAlertArtifact())
    }
    logger.info("Updating observable data type list")
    val dataTypes = dblists.apply("list_artifactDataType")
    Future.sequence(Seq("filename", "fqdn", "url", "user-agent", "domain", "ip", "mail_subject", "hash", "mail",
      "registry", "uri_path", "regexp", "other", "file")
      .map(dt ⇒ dataTypes.addItem(dt).recover { case _ ⇒ () }))
      .map(_ ⇒ ())
      .recover { case _ ⇒ () }
  }

  override val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case DatabaseState(version) if version < 7 ⇒ Nil
    case DatabaseState(7) ⇒
      Seq(
        renameAttribute("reportTemplate", "analyzerId", "analyzers"), // reportTemplate refers only one analyzer
        renameAttribute("reportTemplate", "reportType", "flavor"), // rename flavor into reportType

        removeAttribute("case", "isIncident"), // this information is now stored in resolutionStatus
        mapEntity("case") { c ⇒ // add case owner
          val owner = (c \ "createdBy")
            .asOpt[JsString]
            .getOrElse(JsString("init"))
          c + ("owner" → owner)
        },

        removeEntity("analyzer")(_ ⇒ true), // analyzer is now stored in cortex

        addAttribute("case_artifact", "reports" → JsString("{}")), // add short reports in artifact

        addAttribute("case_task", "order" → JsNumber(0)), // add task order

        addAttribute("user", "preferences" → JsString("{}")), // add user preferences, default empty (Json object)

        mapAttribute(Seq("case", "case_task", "case_task_log", "case_artifact", "audit", "case_artifact_job"), "startDate")(convertDate),
        mapAttribute(Seq("case", "case_task", "case_artifact_job"), "endDate")(convertDate),
        mapAttribute("misp", "date")(convertDate),
        mapAttribute("misp", "publishDate")(convertDate),
        mapAttribute(_ ⇒ true, "createdAt", convertDate),
        mapAttribute(_ ⇒ true, "updatedAt", convertDate))
    case DatabaseState(8) ⇒
      requireUpdateMispAlertArtifact = true
      val hasher = Hasher("MD5")
      Seq(
        renameEntity("misp", "alert"),
        mapEntity("alert") { misp ⇒
          val eventId = (misp \ "eventId").as[Long].toString
          val date = (misp \ "date").as[Date]
          val mispTags = (misp \ "tags").asOpt[Seq[String]].getOrElse(Nil)
          val tags = mispTags.filterNot(_.toLowerCase.startsWith("tlp:")) :+ s"src:${(misp \ "org").as[String]}"
          val tlp = mispTags
            .map(_.toLowerCase)
            .collectFirst {
              case "tlp:white" ⇒ 0L
              case "tlp:green" ⇒ 1L
              case "tlp:amber" ⇒ 2L
              case "tlp:red"   ⇒ 3L
            }
            .getOrElse(2L)
          val source = (misp \ "serverId").asOpt[String].getOrElse("<null>")
          val _id = hasher.fromString(s"misp|$source|$eventId").head.toString()
          (misp \ "caze").asOpt[JsString].fold(JsObject(Nil))(c ⇒ Json.obj("caze" → c)) ++
            Json.obj(
              "_type" → "alert",
              "_id" → _id,
              "type" → "misp",
              "source" → source,
              "sourceRef" → eventId,
              "date" → date,
              "lastSyncDate" → (misp \ "publishDate").as[Date],
              "title" → ("#" + eventId + " " + (misp \ "info").as[String]).trim,
              "description" → s"Imported from MISP Event #$eventId, created at $date",
              "severity" → (misp \ "threatLevel").as[JsNumber],
              "tags" → tags,
              "tlp" → tlp,
              "artifacts" → JsArray(),
              "caseTemplate" → mispCaseTemplate,
              "status" → (misp \ "eventStatus").as[JsString],
              "follow" → (misp \ "follow").as[JsBoolean])
        },
        removeEntity("audit")(o ⇒ (o \ "objectType").asOpt[String].contains("alert")))
    case ds @ DatabaseState(9) ⇒
      object Base64 {
        def unapply(data: String): Option[Array[Byte]] = Try(java.util.Base64.getDecoder.decode(data)).toOption
      }

      // store attachment id and check to prevent document already exists error
      var dataIds = Set.empty[String]
      def containsOrAdd(id: String) = {
        dataIds.synchronized {
          if (dataIds.contains(id)) true
          else {
            dataIds = dataIds + id
            false
          }
        }
      }

      val mainHasher = Hasher(mainHash)
      val extraHashers = Hasher(mainHash +: extraHashes: _*)
      Seq(
        // store alert attachment in datastore
        Operation((f: String ⇒ Source[JsObject, NotUsed]) ⇒ {
          case "alert" ⇒ f("alert").flatMapConcat { alert ⇒
            val artifactsAndData = Future.traverse((alert \ "artifacts").asOpt[List[JsObject]].getOrElse(Nil)) { artifact ⇒
              val isFile = (artifact \ "dataType").asOpt[String].contains("file")
              // get MISP attachment
              if (!isFile)
                Future.successful(artifact → Nil)
              else {
                (for {
                  dataStr ← (artifact \ "data").asOpt[String]
                  dataJson ← Try(Json.parse(dataStr)).toOption
                  dataObj ← dataJson.asOpt[JsObject]
                  filename ← (dataObj \ "filename").asOpt[String].map(_.split("|").head)
                  attributeId ← (dataObj \ "attributeId").asOpt[String]
                  attributeType ← (dataObj \ "attributeType").asOpt[String]
                } yield Future.successful((artifact - "data" + ("remoteAttachment" → Json.obj(
                  "reference" → attributeId,
                  "filename" → filename,
                  "type" → attributeType))) → Nil))
                  .orElse {
                    (artifact \ "data").asOpt[String]
                      .collect {
                        // get attachment encoded in data field
                        case AlertSrv.dataExtractor(filename, contentType, data @ Base64(rawData)) ⇒
                          val attachmentId = mainHasher.fromByteArray(rawData).head.toString()
                          ds.getEntity(datastoreName, s"${attachmentId}_0")
                            .map(_ ⇒ Nil)
                            .recover {
                              case _ if containsOrAdd(attachmentId) ⇒ Nil
                              case _ ⇒
                                Seq(Json.obj(
                                  "_type" → datastoreName,
                                  "_id" → s"${attachmentId}_0",
                                  "data" → data))
                            }
                            .map { dataEntity ⇒
                              val attachment = Attachment(filename, extraHashers.fromByteArray(rawData), rawData.length.toLong, contentType, attachmentId)
                              (artifact - "data" + ("attachment" → Json.toJson(attachment))) → dataEntity
                            }
                      }

                  }
                  .getOrElse(Future.successful(artifact → Nil))
              }
            }
            Source.fromFuture(artifactsAndData)
              .mapConcat { ad ⇒
                val updatedAlert = alert + ("artifacts" → JsArray(ad.map(_._1)))
                updatedAlert :: ad.flatMap(_._2)
              }
          }
          case other ⇒ f(other)
        }),
        // Fix alert status
        mapAttribute("alert", "status") {
          case JsString("Update") ⇒ JsString("Updated")
          case JsString("Ignore") ⇒ JsString("Ignored")
          case other              ⇒ other
        },
        // Fix double encode of metrics
        mapEntity("dblist") {
          case dblist if (dblist \ "dblist").asOpt[String].contains("case_metrics") ⇒
            (dblist \ "value").asOpt[String].map(Json.parse).fold(dblist) { value ⇒
              dblist + ("value" → value)
            }
          case other ⇒ other
        },
        // Add empty metrics and custom fields in cases
        mapEntity("case") { caze ⇒
          val metrics = (caze \ "metrics").asOpt[JsObject].getOrElse(JsObject(Nil))
          val customFields = (caze \ "customFields").asOpt[JsObject].getOrElse(JsObject(Nil))
          caze + ("metrics" → metrics) + ("customFields" → customFields)
        })
    case DatabaseState(10) ⇒ Nil
    case DatabaseState(11) ⇒
      Seq(
        mapEntity("case_task_log") { log ⇒
          val owner = (log \ "createdBy").asOpt[JsString].getOrElse(JsString("init"))
          log + ("owner" → owner)
        },
        mapEntity(_ ⇒ true, entity ⇒ entity - "user"))
  }

  private val requestCounter = new java.util.concurrent.atomic.AtomicInteger(0)

  def getRequestId: String = {
    utils.Instance.id + ":mig:" + requestCounter.incrementAndGet()
  }

  def convertDate(json: JsValue): JsValue = {
    val datePattern = "yyyyMMdd'T'HHmmssZ"
    val dateReads = Reads.dateReads(datePattern).orElse(Reads.DefaultDateReads)
    val date = dateReads.reads(json).getOrElse {
      logger.warn(s"""Invalid date format : "$json" setting now""")
      new Date
    }
    org.elastic4play.JsonFormat.dateFormat.writes(date)
  }

  def removeDot[A <: JsValue](json: A): A = json match {
    case obj: JsObject ⇒
      obj.map {
        case (key, value) if key.contains(".") ⇒
          val splittedKey = key.split("\\.")
          splittedKey.head → splittedKey.tail.foldRight(removeDot(value))((k, v) ⇒ JsObject(Seq(k → v)))
        case (key, value) ⇒ key → removeDot(value)
      }
        .asInstanceOf[A]
    case other ⇒ other
  }

  def auditDetailsCleanup(audit: JsObject): JsObject = removeDot {
    // get audit details
    (audit \ "details").asOpt[JsObject]
      .flatMap { details ⇒
        // get object type of audited object
        (audit \ "objectType")
          .asOpt[String]
          // find related model
          .flatMap(objectType ⇒ models.find(_.modelName == objectType))
          // and get name of audited attributes
          .map(_.attributes.collect {
            case attr if !attr.isUnaudited ⇒ attr.attributeName
          })
          .map { attributes ⇒
            // put audited attribute in details and unaudited in otherDetails
            val otherDetails = (audit \ "otherDetails")
              .asOpt[String]
              .flatMap(od ⇒ Try(Json.parse(od).as[JsObject]).toOption)
              .getOrElse(JsObject(Nil))
            val (in, notIn) = details.fields.partition(f ⇒ attributes.contains(f._1.split("\\.").head))
            val newOtherDetails = otherDetails ++ JsObject(notIn)
            audit + ("details" → JsObject(in)) + ("otherDetails" → JsString(newOtherDetails.toString.take(10000)))
          }
      }
      .getOrElse(audit)
  }

  def addAuditRequestId(audit: JsObject): JsObject = (audit \ "requestId").asOpt[String] match {
    case None if (audit \ "base").toOption.isDefined ⇒ audit + ("requestId" → JsString(getRequestId))
    case None                                        ⇒ audit + ("requestId" → JsString(getRequestId)) + ("base" → JsBoolean(true))
    case _                                           ⇒ audit
  }
}