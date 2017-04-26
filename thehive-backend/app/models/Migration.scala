package models

import java.util.Date
import javax.inject.Inject

import akka.stream.Materializer
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services._
import org.elastic4play.utils
import org.elastic4play.utils.{ Hasher, RichJson }
import play.api.{ Configuration, Logger }
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import scala.collection.immutable.{ Set ⇒ ISet }
import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Try

case class UpdateMispAlertArtifact() extends EventMessage

class Migration(
    mispCaseTemplate: Option[String],
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
    this(configuration.getString("misp.caseTemplate"), models, dblists, eventSrv, ec, materializer)
  }

  import org.elastic4play.services.Operation._
  val logger = Logger(getClass)
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

        removeEntity("analyzer")(_ ⇒ false), // analyzer is now stored in cortex

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
        })
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
          .flatMap(objectType ⇒ models.find(_.name == objectType))
          // and get name of audited attributes
          .map(_.attributes.collect {
            case attr if !attr.isUnaudited ⇒ attr.name
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