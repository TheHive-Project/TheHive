package models

import java.util.Date

import javax.inject.Inject

import scala.collection.immutable.{ Set => ISet }
import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Try

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import play.api.Logger
import play.api.libs.json.{ JsArray, JsBoolean, JsDefined }
import play.api.libs.json.{ JsNumber, JsObject, JsString, JsValue }
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.JsonFormat.dateFormat
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.{ DBLists, DatabaseState, MigrationOperations, Operation }
import org.elastic4play.utils
import org.elastic4play.utils.{ Hasher, RichFuture, RichJson }

class Migration @Inject() (
    models: ISet[BaseModelDef],
    dblists: DBLists,
    implicit val ec: ExecutionContext,
    implicit val materializer: Materializer) extends MigrationOperations {
  import Operation._
  val log = Logger(getClass)

  override def beginMigration(version: Int) = Future.successful(())

  override def endMigration(version: Int) = {
    log.info("Updating observable data type list")
    val dataTypes = dblists.apply("list_artifactDataType")
    Future.sequence(Seq("filename", "fqdn", "url", "user-agent", "domain", "ip", "mail_subject", "hash", "mail", "registry", "uri_path", "regexp", "other", "file")
      .map(dt => dataTypes.addItem(dt).recover { case _ => () }))
      .map(_ => ())
      .recover { case _ => () }
  }

  override val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case previousState @ DatabaseState(1) =>
      // replace content-type by full metadata (from apache tika) in entity that contain attachment
      /*
      mapEntity("event_artifact", "task_log") { entity =>
        (entity \ "hash").asOpt[String] match {
          case Some(hash) => entity - "contentType" + ("metadata" -> dataStore.getMetadata(hash, (entity \ "attachmentName").as[String]))
          case None => entity
        }
      },
      */
      // remove sensitive information (attributes that start with @) in audit details
      Seq(
        mapAttribute("audit", "details") {
          case JsString(details) =>
            Try(Json.parse(details)).map({
              case JsObject(fields) => JsObject(fields.filterNot(_._1.startsWith("@")))
              case a: JsValue       => a
            }).getOrElse(JsObject(Seq("value" -> JsString(details))))
          case details => JsObject(Seq("value" -> details))
        },
        // don't use document version to store sequence value
        //        Operation((req: String => Source[JsObject, NotUsed]) => {
        //          case tableName @ "sequence" =>
        //            for {
        //              s <- req(tableName)
        //              id <- (s \ "id").asOpt[String]
        //              previousValue = previousState.getEntity(tableName, id).await
        //              version <- previousValue \ "version" toOption
        //            } yield s - "dummy" + ("value" -> version)
        //          case other => req(other)
        //        }),

        // rename entities
        mapAttribute("sequence", "id") {
          case JsString("event") => JsString("case")
          case x                 => x
        },
        renameEntity("event", "case"),
        renameAttribute("case", "caseId", "eventId"),
        renameEntity("event_artifact", "case_artifact"),
        renameEntity("artifact_job", "case_artifact_job"),
        renameEntity("job_log", "case_artifact_job_log"),
        renameEntity("event_task", "case_task"),
        renameEntity("task_log", "case_task_log"),
        renameAttribute("user", "name", "full-name"),
        // Tag key as private and rootId as non-private
        renameAttribute("user", "@key", "key"),
        renameAttribute("audit", "rootId", "@rootId"),
        // Use user login as entity id
        mapEntity("user") { user =>
          user \ "login" match {
            case JsDefined(login: JsString) => user - "id" - "login" + ("id" -> login)
            case _                          => user
          }
        },
        // replace user id by user login in Audit
        mapEntity("audit") { audit =>
          val userMapping = previousState.source("user")
            .mapConcat { user =>
              (for {
                id <- (user \ "id").asOpt[String]
                login <- (user \ "login").asOpt[String]
              } yield id -> login).toList
            }
            .runWith(Sink.seq)
            .await
            .toMap

          (for {
            objectType <- (audit \ "objectType").asOpt[String]
            if objectType == "user"
            id <- (audit \ "objectId").asOpt[String]
            login <- userMapping.get(id)
          } yield audit + ("objectId" -> JsString(login))) getOrElse audit
        })

    case previousState @ DatabaseState(2) =>
      Seq(
        // Add flag in task
        addAttribute("case_task", "flag" -> JsBoolean(false)),
        // Add flag in Case
        addAttribute("case", "flag" -> JsBoolean(false)),
        // Set TLP if attribute is not present in Artifact
        addAttributeIfAbsent("case_artifact", "tlp" -> JsNumber(-1)),
        // Add IOC and Label in Artifact
        addAttribute("case_artifact", "ioc" -> JsBoolean(false), "labels" -> JsArray(Nil)),
        // Rename command by @command in analyzer
        renameAttribute("analyzer", "@command", "command"),
        // Fix attribute type in job reports
        mapEntity("case_artifact_job") { job =>
          val analyzerId = (job \ "analyzerId").asOpt[String].getOrElse {
            //log.error("Job entity has invalid attributes : analyzerId is missing : " + job)
            "unknown"
          }
          rename(job, "error_message", "report" :: analyzerId :: "value" :: Nil)
        },
        // Transform comma separated list by array
        mapAttribute("user", "roles") {
          case JsString(roles) => JsArray(roles.split(",").toSeq.map(JsString))
          case x               => x
        },
        mapAttribute("case", "tags") {
          case JsString(tags) => JsArray(tags.split(",").toSeq.map(JsString))
          case x              => x
        })
    case previousState @ DatabaseState(3) =>
      // artifact data convert from textFmt into stringFmt (become not analyzed)  
      // no operation required
      Nil
    case previousState @ DatabaseState(4) =>
      // saved filesize from datastore in order to insert it in artifacts & logs
      var attachmentSizes = Map.empty[String, Long]

      Seq(
        removeEntity("data") { data =>
          (for {
            fileSize <- (data \ "fileSize").asOpt[Long]
            id <- (data \ "id").asOpt[String]
          } yield {

            attachmentSizes += id -> fileSize
            false
          }) getOrElse (true)
        },
        //      attachment is now an object (no more attributes for metadata nor hash)
        mapEntity("case_artifact", "case_task_log") { obj =>
          (obj \ "hash").asOpt[String].fold(obj) { hash =>
            val attachmentSize = attachmentSizes.getOrElse(hash, 0L)
            val attachmentName = (obj \ "attachmentName").asOpt[String].getOrElse("noname")
            val contentType = (obj \ "metadata" \ "Content-Type").asOpt[String].getOrElse("application/octet-stream")
            obj - "hash" - "metadata" - "attachmentName" + (
              "attachment" -> Json.obj(
                "id" -> hash,
                "hashes" -> JsArray(Nil),
                "name" -> attachmentName,
                "size" -> attachmentSize,
                "contentType" -> contentType))
          }
        },
        // convert dblist format
        // before 1 entry for each dblist, after 1 entry for each dblist item
        Operation((f: String => Source[JsObject, NotUsed]) => {
          case table @ "dblist" => f(table).mapConcat { list =>
            for {
              listName <- (list \ "id").asOpt[String].toList
              items <- (list \ listName).asOpt[Seq[JsValue]].toList
              item <- items
              id = Hasher("MD5").fromString(item.toString).head.toString
            } yield JsObject(Seq("id" -> JsString(id), "dblist" -> JsString(listName), "value" -> JsString(item.toString)))
          }
          case other => f(other)
        }),
        // Add resolutionStatus and summary attributes to Case entity
        addAttribute("case", "resolutionStatus" -> JsString("Unknown"), "summary" -> JsString("")),
        // Set case TLP to AMBER(2) by default instead of not specified(-1)
        mapAttribute("case", "tlp") {
          case JsNumber(x) if x == -1 => JsNumber(2)
          case x                      => x
        },
        // Set observable TLP to AMBER(2) by default instead of not specified(-1)
        mapAttribute("case_artifact", "tlp") {
          case JsNumber(x) if x == -1 => JsNumber(2)
          case x                      => x
        })

    case previousState @ DatabaseState(5) =>
      Seq(
        renameAttribute("case_artifact", "tags", "labels"),
        renameAttribute("user", "password", "@password"),
        renameAttribute("user", "key", "@key"),
        renameAttribute("data", "binary", "data"),
        removeAttribute("data", "chunkCount", "fileSize"),
        renameAttribute("sequence", "counter", "value"),
        removeAttribute(_ => true, "$routing"),
        removeAttribute("case", "ioc"),
        mapAttribute("case", "resolutionStatus") {
          case JsString("Unknown")     => JsString("Indeterminate")
          case JsString("NotIncident") => JsString("Other")
          case x                       => x
        },
        mapAttribute("case_artifact_job", "report") { report => JsString(report.toString) },
        removeAttribute("analyzer", "@baseConfig", "@command", "@config", "@report"),
        renameEntity("template", "caseTemplate"),
        renameAttribute("caseTemplate", "metricNames", "metrics"),
        mapEntity("audit")(auditDetailsCleanup),
        mapEntity("audit")(addAuditRequestId))

    case previousState @ DatabaseState(6) =>
      Seq(
        mapEntity(_ => true, e => {
          val createdAt = (e \ "startDate")
            .asOpt[JsString]
            .getOrElse(Json.toJson(new Date))
          val createdBy = (e \ "user")
            .asOpt[JsString]
            .getOrElse(JsString("init"))
          e +
            ("createdAt" -> createdAt) +
            ("createdBy" -> createdBy)
        }))
  }

  private val requestCounter = new java.util.concurrent.atomic.AtomicInteger(0)
  def getRequestId = {
    utils.Instance.id + ":mig:" + requestCounter.incrementAndGet()
  }

  def removeDot[A <: JsValue](json: A): A = json match {
    case obj: JsObject =>
      obj.map {
        case (key, value) if key.contains(".") =>
          val splittedKey = key.split("\\.")
          splittedKey.head -> splittedKey.tail.foldRight(removeDot(value))((k, v) => JsObject(Seq(k -> v)))
        case (key, value) => key -> removeDot(value)
      }
        .asInstanceOf[A]
    case other => other
  }

  def auditDetailsCleanup(audit: JsObject): JsObject = removeDot {
    // get audit details
    (audit \ "details").asOpt[JsObject]
      .flatMap { details =>
        // get object type of audited object
        (audit \ "objectType")
          .asOpt[String]
          // find related model
          .flatMap(objectType => models.find(_.name == objectType))
          // and get name of audited attributes
          .map(_.attributes.collect {
            case attr if !attr.isUnaudited => attr.name
          })
          .map { attributes =>
            // put audited attribute in details and unaudited in otherDetails
            val otherDetails = (audit \ "otherDetails").asOpt[String].flatMap(od => Try(Json.parse(od).as[JsObject]).toOption).getOrElse(JsObject(Nil))
            val (in, notIn) = details.fields.partition(f => attributes.contains(f._1.split("\\.").head))
            val newOtherDetails = otherDetails ++ JsObject(notIn)
            audit + ("details" -> JsObject(in)) + ("otherDetails" -> JsString(newOtherDetails.toString.take(10000)))
          }
      }
      .getOrElse(audit)
  }

  def addAuditRequestId(audit: JsObject): JsObject = (audit \ "requestId").asOpt[String] match {
    case None if (audit \ "base").toOption.isDefined => audit + ("requestId" -> JsString(getRequestId))
    case None                                        => audit + ("requestId" -> JsString(getRequestId)) + ("base" -> JsBoolean(true))
    case _                                           => audit
  }
}