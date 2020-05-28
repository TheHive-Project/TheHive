package models

import java.nio.file.{Files, Path}
import java.util.Date

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import javax.inject.{Inject, Singleton}
import org.elastic4play.ConflictError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services._
import org.elastic4play.utils.Hasher
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._
import play.api.{Configuration, Environment, Logger}
import services.{AlertSrv, DashboardSrv}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal.int2bigDecimal
import scala.util.Try

case class UpdateMispAlertArtifact() extends EventMessage

@Singleton
class Migration(
    mispCaseTemplate: Option[String],
    mainHash: String,
    extraHashes: Seq[String],
    datastoreName: String,
    dblists: DBLists,
    eventSrv: EventSrv,
    dashboardSrv: DashboardSrv,
    userSrv: UserSrv,
    environment: Environment,
    implicit val ec: ExecutionContext,
    implicit val materializer: Materializer
) extends MigrationOperations {
  @Inject() def this(
      configuration: Configuration,
      dblists: DBLists,
      eventSrv: EventSrv,
      dashboardSrv: DashboardSrv,
      userSrv: UserSrv,
      environment: Environment,
      ec: ExecutionContext,
      materializer: Materializer
  ) = {
    this(
      configuration.getOptional[String]("misp.caseTemplate"),
      configuration.get[String]("datastore.hash.main"),
      configuration.get[Seq[String]]("datastore.hash.extra"),
      configuration.get[String]("datastore.name"),
      dblists,
      eventSrv,
      dashboardSrv,
      userSrv,
      environment,
      ec,
      materializer
    )
  }

  import org.elastic4play.services.Operation._

  private[Migration] lazy val logger         = Logger(getClass)
  private var requireUpdateMispAlertArtifact = false

  override def beginMigration(version: Int): Future[Unit] = Future.successful(())

  private def readJsonFile(file: Path): JsValue = {
    val source = scala.io.Source.fromFile(file.toFile)
    try Json.parse(source.mkString)
    finally source.close()
  }

  private def addDataTypes(dataTypes: Seq[String]): Future[Unit] = {
    val dataTypeList = dblists.apply("list_artifactDataType")
    Future
      .traverse(dataTypes) { dt ⇒
        dataTypeList
          .addItem(dt)
          .map(_ ⇒ ())
          .recover {
            case _: ConflictError ⇒
            case error            ⇒ logger.error(s"Failed to add dataType $dt during migration", error)
          }
      }
      .map(_ ⇒ ())
  }

  private def addDashboards(version: Int): Future[Unit] =
    dashboardSrv.find(QueryDSL.any, Some("0-0"), Nil)._2.flatMap {
      case 0 ⇒
        userSrv.inInitAuthContext { implicit authContext ⇒
          val dashboardsPath = environment.rootPath.toPath.resolve("migration").resolve("12").resolve("dashboards")
          val dashboards = for {
            dashboardFile ← Try(Files.newDirectoryStream(dashboardsPath, "*.json").asScala).getOrElse(Nil)
            if Files.isReadable(dashboardFile)
            dashboardJson ← Try(readJsonFile(dashboardFile).as[JsObject]).toOption
            dashboardDefinition = (dashboardJson \ "definition").as[JsValue].toString
            dash = dashboardSrv
              .create(Fields(dashboardJson + ("definition" → JsString(dashboardDefinition))))
              .map(_ ⇒ ())
              .recover {
                case error ⇒ logger.error(s"Failed to create dashboard $dashboardFile during migration", error)
              }
          } yield dash
          Future.sequence(dashboards).map(_ ⇒ ())
        }
      case _ ⇒ Future.successful(())
    }

  override def endMigration(version: Int): Future[Unit] = {
    if (requireUpdateMispAlertArtifact) {
      logger.info("Retrieve MISP attribute to update alerts")
      eventSrv.publish(UpdateMispAlertArtifact())
    }
    logger.info("Updating observable data type list")
    addDataTypes(
      Seq(
        "filename",
        "fqdn",
        "url",
        "user-agent",
        "domain",
        "ip",
        "mail_subject",
        "hash",
        "mail",
        "registry",
        "uri_path",
        "regexp",
        "other",
        "file",
        "autonomous-system"
      )
    ).andThen { case _ ⇒ addDashboards(version + 1) }

  }

  override val operations: PartialFunction[DatabaseState, Seq[Operation]] = {
    case DatabaseState(version) if version < 7 ⇒ Nil
    case DatabaseState(7) ⇒
      Seq(
        renameAttribute("reportTemplate", "analyzerId", "analyzers"), // reportTemplate refers only one analyzer
        renameAttribute("reportTemplate", "reportType", "flavor"),    // rename flavor into reportType
        removeAttribute("case", "isIncident"),                        // this information is now stored in resolutionStatus
        mapEntity("case") { c ⇒                                       // add case owner
          val owner = (c \ "createdBy")
            .asOpt[JsString]
            .getOrElse(JsString("init"))
          c + ("owner" → owner)
        },
        removeEntity("analyzer")(_ ⇒ true), // analyzer is now stored in cortex
        addAttribute("case_artifact", "reports" → JsString("{}")), // add short reports in artifact
        addAttribute("case_task", "order"       → JsNumber(0)),    // add task order
        addAttribute("user", "preferences"      → JsString("{}")), // add user preferences, default empty (Json object)
        mapAttribute(Seq("case", "case_task", "case_task_log", "case_artifact", "audit", "case_artifact_job"), "startDate")(convertDate),
        mapAttribute(Seq("case", "case_task", "case_artifact_job"), "endDate")(convertDate),
        mapAttribute("misp", "date")(convertDate),
        mapAttribute("misp", "publishDate")(convertDate),
        mapAttribute(_ ⇒ true, "createdAt", convertDate),
        mapAttribute(_ ⇒ true, "updatedAt", convertDate)
      )
    case DatabaseState(8) ⇒
      requireUpdateMispAlertArtifact = true
      val hasher = Hasher("MD5")
      Seq(
        renameEntity("misp", "alert"),
        mapEntity("alert") { misp ⇒
          val eventId  = (misp \ "eventId").as[Long].toString
          val date     = (misp \ "date").as[Date]
          val mispTags = (misp \ "tags").asOpt[Seq[String]].getOrElse(Nil)
          val tags     = mispTags.filterNot(_.toLowerCase.startsWith("tlp:")) :+ s"src:${(misp \ "org").as[String]}"
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
          val _id    = hasher.fromString(s"misp|$source|$eventId").head.toString()
          (misp \ "caze").asOpt[JsString].fold(JsObject.empty)(c ⇒ Json.obj("caze" → c)) ++
            Json.obj(
              "_type"        → "alert",
              "_id"          → _id,
              "type"         → "misp",
              "source"       → source,
              "sourceRef"    → eventId,
              "date"         → date,
              "lastSyncDate" → (misp \ "publishDate").as[Date],
              "title"        → ("#" + eventId + " " + (misp \ "info").as[String]).trim,
              "description"  → s"Imported from MISP Event #$eventId, created at $date",
              "severity"     → (misp \ "threatLevel").as[JsNumber],
              "tags"         → tags,
              "tlp"          → tlp,
              "artifacts"    → JsArray(),
              "caseTemplate" → mispCaseTemplate,
              "status"       → (misp \ "eventStatus").as[JsString],
              "follow"       → (misp \ "follow").as[JsBoolean]
            )
        },
        removeEntity("audit")(o ⇒ (o \ "objectType").asOpt[String].contains("alert"))
      )
    case ds @ DatabaseState(9) ⇒
      object Base64 {
        def unapply(data: String): Option[Array[Byte]] = Try(java.util.Base64.getDecoder.decode(data)).toOption
      }

      // store attachment id and check to prevent document already exists error
      var dataIds = Set.empty[String]

      def containsOrAdd(id: String) =
        dataIds.synchronized {
          if (dataIds.contains(id)) true
          else {
            dataIds = dataIds + id
            false
          }
        }

      val mainHasher   = Hasher(mainHash)
      val extraHashers = Hasher(mainHash +: extraHashes: _*)
      Seq(
        // store alert attachment in datastore
        Operation((f: String ⇒ Source[JsObject, NotUsed]) ⇒ {
          case "alert" ⇒
            f("alert").flatMapConcat {
              alert ⇒
                val artifactsAndData = Future.traverse((alert \ "artifacts").asOpt[List[JsObject]].getOrElse(Nil)) {
                  artifact ⇒
                    val isFile = (artifact \ "dataType").asOpt[String].contains("file")
                    // get MISP attachment
                    if (!isFile)
                      Future.successful(artifact → Nil)
                    else {
                      (for {
                        dataStr       ← (artifact \ "data").asOpt[String]
                        dataJson      ← Try(Json.parse(dataStr)).toOption
                        dataObj       ← dataJson.asOpt[JsObject]
                        filename      ← (dataObj \ "filename").asOpt[String].map(_.split("\\|").head)
                        attributeId   ← (dataObj \ "attributeId").asOpt[String]
                        attributeType ← (dataObj \ "attributeType").asOpt[String]
                      } yield Future.successful(
                        (artifact - "data" + ("remoteAttachment" → Json
                          .obj("reference" → attributeId, "filename" → filename, "type" → attributeType))) → Nil
                      )).orElse {
                          (artifact \ "data")
                            .asOpt[String]
                            .collect {
                              // get attachment encoded in data field
                              case AlertSrv.dataExtractor(filename, contentType, data @ Base64(rawData)) ⇒
                                val attachmentId = mainHasher.fromByteArray(rawData).head.toString()
                                ds.getEntity(datastoreName, s"${attachmentId}_0")
                                  .map(_ ⇒ Nil)
                                  .recover {
                                    case _ if containsOrAdd(attachmentId) ⇒ Nil
                                    case _ ⇒
                                      Seq(Json.obj("_type" → datastoreName, "_id" → s"${attachmentId}_0", "data" → data))
                                  }
                                  .map { dataEntity ⇒
                                    val attachment =
                                      Attachment(filename, extraHashers.fromByteArray(rawData), rawData.length.toLong, contentType, attachmentId)
                                    (artifact - "data" + ("attachment" → Json.toJson(attachment))) → dataEntity
                                  }
                            }

                        }
                        .getOrElse(Future.successful(artifact → Nil))
                    }
                }
                Source
                  .future(artifactsAndData)
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
          val metrics      = (caze \ "metrics").asOpt[JsObject].getOrElse(JsObject.empty)
          val customFields = (caze \ "customFields").asOpt[JsObject].getOrElse(JsObject.empty)
          caze + ("metrics" → metrics) + ("customFields" → customFields)
        }
      )
    case DatabaseState(10) ⇒ Nil
    case DatabaseState(11) ⇒
      Seq(
        mapEntity("case_task_log") { log ⇒
          val owner = (log \ "createdBy").asOpt[JsString].getOrElse(JsString("init"))
          log + ("owner" → owner)
        },
        mapEntity(_ ⇒ true, entity ⇒ entity - "user"),
        mapEntity("caseTemplate") { caseTemplate ⇒
          val metricsName = (caseTemplate \ "metricNames").asOpt[Seq[String]].getOrElse(Nil)
          val metrics     = JsObject(metricsName.map(_ → JsNull))
          caseTemplate - "metricNames" + ("metrics" → metrics)
        },
        addAttribute("case_artifact", "sighted" → JsFalse)
      )
    case ds @ DatabaseState(12) ⇒
      Seq(
        // Remove alert artifacts in audit trail
        mapEntity("audit") {
          case audit if (audit \ "objectType").asOpt[String].contains("alert") ⇒
            (audit \ "details").asOpt[JsObject].fold(audit) { details ⇒
              audit + ("details" → (details - "artifacts"))
            }
          case audit ⇒ audit
        },
        // Regenerate all alert ID
        mapEntity("alert") { alert ⇒
          val alertId = JsString(generateAlertId(alert))
          alert + ("_id" → alertId) + ("_routing" → alertId)
        },
        // and overwrite alert id in audit trail
        Operation((f: String ⇒ Source[JsObject, NotUsed]) ⇒ {
          case "audit" ⇒
            f("audit").flatMapConcat {
              case audit if (audit \ "objectType").asOpt[String].contains("alert") ⇒
                val updatedAudit = (audit \ "objectId").asOpt[String].fold(Future.successful(audit)) { alertId ⇒
                  ds.getEntity("alert", alertId)
                    .map { alert ⇒
                      audit + ("objectId" → JsString(generateAlertId(alert)))
                    }
                    .recover {
                      case e ⇒
                        logger.error(s"Get alert $alertId", e)
                        audit
                    }
                }
                Source.future(updatedAudit)
              case audit ⇒ Source.single(audit)
            }
          case other ⇒ f(other)
        })
      )

    case DatabaseState(13) ⇒
      Seq(
        addAttribute("alert", "customFields" → JsObject.empty),
        addAttribute("case_task", "group"    → JsString("default")),
        addAttribute("case", "pap"           → JsNumber(2))
      )
    case DatabaseState(14) ⇒
      Seq(
        mapEntity("sequence") { seq ⇒
          val oldId   = (seq \ "_id").as[String]
          val counter = (seq \ "counter").as[JsNumber]
          seq - "counter" - "_routing" +
            ("_id"             → JsString("sequence_" + oldId)) +
            ("sequenceCounter" → counter)
        }
      )
    case DatabaseState(15) ⇒ Nil
  }

  private def generateAlertId(alert: JsObject): String = {
    val hasher    = Hasher("MD5")
    val tpe       = (alert \ "type").asOpt[String].getOrElse("<null>")
    val source    = (alert \ "source").asOpt[String].getOrElse("<null>")
    val sourceRef = (alert \ "sourceRef").asOpt[String].getOrElse("<null>")
    hasher.fromString(s"$tpe|$source|$sourceRef").head.toString()
  }

  private def convertDate(json: JsValue): JsValue = {
    val datePattern = "yyyyMMdd'T'HHmmssZ"
    val dateReads   = Reads.dateReads(datePattern).orElse(Reads.DefaultDateReads)
    val date = dateReads.reads(json).getOrElse {
      logger.warn(s"""Invalid date format : "$json" setting now""")
      new Date
    }
    org.elastic4play.JsonFormat.dateFormat.writes(date)
  }
}
