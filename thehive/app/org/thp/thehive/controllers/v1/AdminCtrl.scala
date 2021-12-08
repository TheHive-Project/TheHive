package org.thp.thehive.controllers.v1

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import ch.qos.logback.classic.{Level, LoggerContext}
import com.softwaremill.tagging.@@
import org.slf4j.LoggerFactory
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.GenIntegrityCheckOps
import org.thp.thehive.models.Permissions
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsObject, Json, OWrites}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class AdminCtrl(
    entrypoint: Entrypoint,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag,
    integrityCheckOps: Seq[GenIntegrityCheckOps],
    db: Database,
    schemas: Seq[UpdatableSchema],
    implicit val ec: ExecutionContext
) {

  implicit val timeout: Timeout                      = Timeout(5.seconds)
  implicit val checkStatsWrites: OWrites[CheckStats] = Json.writes[CheckStats]
  implicit val checkStateWrites: OWrites[CheckState] = OWrites[CheckState] { state =>
    Json.obj(
      "needCheck"              -> state.needCheck,
      "duplicateTimer"         -> state.duplicateTimer.isDefined,
      "duplicateStats"         -> state.duplicateStats,
      "globalStats"            -> state.globalStats,
      "globalCheckRequestTime" -> state.globalCheckRequestTime
    )
  }
  lazy val logger: Logger = Logger(getClass)

  def setLogLevel(packageName: String, levelName: String): Action[AnyContent] =
    entrypoint("Update log level")
      .authPermitted(Permissions.managePlatform) { _ =>
        val level = levelName match {
          case "ALL"   => Level.ALL
          case "DEBUG" => Level.DEBUG
          case "INFO"  => Level.INFO
          case "WARN"  => Level.WARN
          case "ERROR" => Level.ERROR
          case "OFF"   => Level.OFF
          case "TRACE" => Level.TRACE
          case _       => Level.INFO
        }
        val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
        val logger        = loggerContext.getLogger(packageName)
        logger.setLevel(level)
        Success(Results.NoContent)
      }

  def triggerCheck(name: String): Action[AnyContent] =
    entrypoint("Trigger check")
      .authPermitted(Permissions.managePlatform) { _ =>
        integrityCheckActor ! GlobalCheckRequest(name)
        Success(Results.NoContent)
      }

  def checkStats: Action[AnyContent] =
    entrypoint("Get check stats")
      .asyncAuthPermitted(Permissions.managePlatform) { _ =>
        Future
          .traverse(integrityCheckOps) { c =>
            (integrityCheckActor ? GetCheckStats(c.name))
              .mapTo[CheckState]
              .recover {
                case error =>
                  logger.error(s"Fail to get check stats of ${c.name}", error)
                  CheckState.empty
              }
              .map(c.name -> _)
          }
          .map { results =>
            Results.Ok(JsObject(results.map(r => r._1 -> Json.toJson(r._2))))
          }
      }

  val labels = Seq(
    "Config",
    "ReportTag",
    "KeyValue",
    "Pattern",
    "Case",
    "Procedure",
    "Alert",
    "Dashboard",
    "Observable",
    "User",
    "AnalyzerTemplate",
    "Taxonomy",
    "CustomField",
    "Data",
    "Organisation",
    "Profile",
    "Task",
    "Action",
    "Log",
    "CaseTemplate",
    "Audit",
    "Tag",
    "Job",
    "Attachment"
  )

  def indexStatus: Action[AnyContent] =
    entrypoint("Get index status")
      .authPermittedRoTransaction(db, Permissions.managePlatform) { _ => graph =>
        val indices = labels.map { label =>
          val count =
            try graph.indexCountQuery(s"""v."_label":$label""")
            catch {
              case NonFatal(error) =>
                logger.error("Index fetch error", error)
                0L
            }
          Json.obj("name" -> label, "count" -> count)

        }
        val indexCount = Json.obj("name" -> "global", "indices" -> indices)
        Success(Results.Ok(Json.obj("index" -> Seq(indexCount))))
      }

  def reindex(label: String): Action[AnyContent] =
    entrypoint("Reindex data")
      .authPermitted(Permissions.managePlatform) { _ =>
        Future(db.reindexData(label))
        Success(Results.NoContent)
      }

  def rebuild(name: String): Action[AnyContent] =
    entrypoint("Rebuild index")
      .authPermitted(Permissions.managePlatform) { _ =>
        val removalResult =
          if (name == "all") db.removeAllIndex()
          else db.removeIndex(name, IndexType.fulltext, Nil)
        schemas
          .toTry(db.addSchemaIndexes)
          .flatMap(_ => removalResult)
          .map(_ => Results.NoContent)
      }

  private val rangeRegex = "(\\d+)-(\\d+)".r
  private def getOperations(schemaName: String, select: Option[String], filter: Option[String]): Seq[(Operation, Int)] = {
    val ranges = select.fold(Seq(0 until Int.MaxValue))(_.split(',').toSeq.map {
      case rangeRegex(from, to) => from.toInt to to.toInt
      case number               => number.toInt to number.toInt
    })

    val filters = filter.fold(Seq("all"))(_.split(',').toSeq)

    schemas
      .filter(_.name == schemaName)
      .flatMap { schema =>
        schema
          .operations
          .operations
          .zipWithIndex
          .filter {
            case (_, i) => ranges.exists(_.contains(i))
          }
          .filter {
            case (_: AddVertexModel, _) =>
              filters.contains("AddVertexModel") || filters
                .contains("schema") || (filters.contains("all") && !filters.contains("!schema") && !filters.contains("!AddVertexModel"))
            case (_: AddEdgeModel, _) =>
              filter.contains("AddEdgeModel") || filter
                .contains("schema") || (filters.contains("all") && !filter.contains("!schema") && !filter.contains("!AddEdgeModel"))
            case (_: AddProperty, _) =>
              filter.contains("AddProperty") || filter
                .contains("schema") || (filters.contains("all") && !filter.contains("!schema") && !filter.contains("!AddProperty"))
            case (_: RemoveProperty, _) =>
              filter.contains("RemoveProperty") || filter
                .contains("schema") || (filters.contains("all") && !filter.contains("!schema") && !filter.contains("!RemoveProperty"))
            case (_: RemoveVertexLabel, _) =>
              filter.contains("RemoveVertexLabel") || filter
                .contains("schema") || (filters.contains("all") && !filter.contains("!schema") && !filter.contains("!RemoveVertexLabel"))
            case (_: RemoveEdgeLabel, _) =>
              filter.contains("RemoveEdgeLabel") || filter
                .contains("schema") || (filters.contains("all") && !filter.contains("!schema") && !filter.contains("!RemoveEdgeLabel"))
            case (_: UpdateGraphVertices, _) =>
              filter.contains("UpdateGraph") || filter.contains("UpdateGraphVertices") || filter
                .contains("data") || (filters.contains("all") && !filter.contains("!data") && !filter.contains("!UpdateGraph"))
            case (_: UpdateGraphEdges, _) =>
              filter.contains("UpdateGraph") || filter.contains("UpdateGraphEdges") || filter
                .contains("data") || (filters.contains("all") && !filter.contains("!data") && !filter.contains("!UpdateGraph"))
            case (_: AddIndex, _) =>
              filter.contains("AddIndex") || filter
                .contains("index") || (filters.contains("all") && !filter.contains("!index") && !filter.contains("!AddIndex"))
            case (_: AddIndexedProperty, _) =>
              filter.contains("AddIndexedProperty") || filter
                .contains("index") || (filters.contains("all") && !filter.contains("!index") && !filter.contains("!AddIndexedProperty"))
            case (ReindexData, _) =>
              filter.contains("ReindexData") || filter
                .contains("index") || (filters.contains("all") && !filter.contains("!index") && !filter.contains("!RebuildIndexes"))
            case (NoOperation, _) => false
            case (_: RemoveIndex, _) =>
              filter.contains("RemoveIndex") || filter
                .contains("index") || (filters.contains("all") && !filter.contains("!index") && !filter.contains("!RemoveIndex"))
            case (_: DBOperation[_], _) =>
              filter.contains("DBOperation") || filter
                .contains("data") || (filters.contains("all") && !filter.contains("!data") && !filter.contains("!DBOperation"))
          }
      }
  }

  def schemaRepair(schemaName: String, select: Option[String], filter: Option[String]): Action[AnyContent] =
    entrypoint("Repair schema")
      .authPermitted(Permissions.managePlatform) { _ =>
        val result = getOperations(schemaName, select, filter)
          .map {
            case (operation, index) =>
              logger.info(s"Repair schema: $index=${operation.info}")
              operation.execute(db, logger.info(_)) match {
                case _: Success[_]  => s"${operation.info}: Success"
                case Failure(error) => s"${operation.info}: Failure $error"
              }
          }

        Success(Results.Ok(Json.toJson(result)))
      }

  def schemaInfo(schemaName: String, select: Option[String], filter: Option[String]): Action[AnyContent] =
    entrypoint("Schema info")
      .authPermitted(Permissions.managePlatform) { _ =>
        val output = getOperations(schemaName, select, filter).map {
          case (o, i) => s"$i=${o.info}"
        }
        Success(Results.Ok(Json.toJson(output)))
      }
}
