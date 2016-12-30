package models

import java.util.Date

import javax.inject.Inject

import scala.collection.immutable.{ Set ⇒ ISet }
import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Try

import akka.stream.Materializer

import play.api.Logger
import play.api.libs.json.{ JsBoolean, JsNumber, JsObject, JsString, JsValue }
import play.api.libs.json.{ Json, Reads }
import play.api.libs.json.JsValue.jsValueToJsLookup

import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.{ DBLists, DatabaseState, MigrationOperations, Operation }
import org.elastic4play.utils
import org.elastic4play.utils.RichJson

class Migration @Inject() (
    models: ISet[BaseModelDef],
    dblists: DBLists,
    implicit val ec: ExecutionContext,
    implicit val materializer: Materializer) extends MigrationOperations {
  import org.elastic4play.services.Operation._
  val logger = Logger(getClass)

  override def beginMigration(version: Int) = Future.successful(())

  override def endMigration(version: Int) = {
    log.info("Updating observable data type list")
    val dataTypes = dblists.apply("list_artifactDataType")
    Future.sequence(Seq("filename", "fqdn", "url", "user-agent", "domain", "ip", "mail_subject", "hash", "mail", "registry", "uri_path", "regexp", "other", "file")
      .map(dt ⇒ dataTypes.addItem(dt).recover { case _ ⇒ () }))
      .map(_ ⇒ ())
      .recover { case _ ⇒ () }
  }

  override val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case previousState @ DatabaseState(7) ⇒
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

        mapAttribute(Seq("case", "case_task", "case_task_log", "case_artifact", "audit", "case_artifact_job"), "startDate")(convertDate),
        mapAttribute(Seq("case", "case_task", "case_artifact_job"), "endDate")(convertDate),
        mapAttribute("misp", "date")(convertDate),
        mapAttribute("misp", "publishDate")(convertDate),
        mapAttribute(_ ⇒ true, "createdBy", convertDate),
        mapAttribute(_ ⇒ true, "updatedBy", convertDate))
  }

  private val requestCounter = new java.util.concurrent.atomic.AtomicInteger(0)
  def getRequestId = {
    utils.Instance.id + ":mig:" + requestCounter.incrementAndGet()
  }

  def convertDate(json: JsValue): JsValue = {
    val datePattern = "yyyyMMdd'T'HHmmssZ"
    val dateReads = Reads.dateReads(datePattern).orElse(Reads.DefaultDateReads)
    val date = dateReads.reads(json).getOrElse {
      logger.warn(s"""Invalid date format : "$json" setting now""")
      new Date
    }
    org.elastic4play.JsonFormat.dateWrites.writes(date)
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
            val otherDetails = (audit \ "otherDetails").asOpt[String].flatMap(od ⇒ Try(Json.parse(od).as[JsObject]).toOption).getOrElse(JsObject(Nil))
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