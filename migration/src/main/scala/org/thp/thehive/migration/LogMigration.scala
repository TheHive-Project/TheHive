package org.thp.thehive.migration
import java.util.Date

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{hasParentQuery, idsQuery, search, RichString}
import gremlin.scala.Graph
import javax.inject.Inject
import org.thp.scalligraph.Hasher
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.StorageSrv
import org.thp.thehive.models.{Log, Task}
import org.thp.thehive.services.{AttachmentSrv, LogSrv}

import org.elastic4play.database.DBFind
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services.{Attachment ⇒ ElasticAttachment, AttachmentSrv ⇒ ElasticAttachmentSrv}

class LogMigration @Inject()(
    config: Configuration,
    logSrv: LogSrv,
    toDB: Database,
    fromFind: DBFind,
    userMigration: UserMigration,
    auditMigration: AuditMigration,
    attachmentSrv: AttachmentSrv,
    storageSrv: StorageSrv,
    elasticAttachmentSrv: ElasticAttachmentSrv,
    implicit val mat: Materializer
) extends Utils {
  val hashers = Hasher(config.get[Seq[String]]("attachment.hash"): _*)

  implicit val logReads: Reads[Log] =
    ((JsPath \ "message").read[String] and
      (JsPath \ "startDate").read[Date] and
      (JsPath \ "status").readNullable[String].map(_.contains("Deleted")))(Log.apply _)

  def importLogs(taskId: String, task: Task with Entity, progress: ProgressBar)(implicit graph: Graph): Unit = {
    val done = fromFind(Some("all"), Nil)(
      index ⇒
        search(index / "case_task_log")
          .query(hasParentQuery("case_task", idsQuery(taskId), score = false))
    )._1
      .map { logJs ⇒
        catchError("Log", logJs, progress) {
          userMigration.withUser((logJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
            val log = logJs.as[Log]
//            logger.info(s"Importing log ${task.title} / ${log.message}")
            logSrv.create(log, task).map { createdLog ⇒
              auditMigration.importAudits("case_task_log", (logJs \ "_id").as[String], createdLog, progress)
              (logJs \ "attachment")
                .asOpt[ElasticAttachment]
                .map(saveAttachment(attachmentSrv, storageSrv, elasticAttachmentSrv, hashers, toDB)(_))
                .foreach(logSrv.addAttachment(createdLog, _))
            }
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

}
