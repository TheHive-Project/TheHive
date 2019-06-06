package org.thp.thehive.migration
import java.util.Date

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{hasParentQuery, idsQuery, search, RichString}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.{Case, Task, TaskStatus}
import org.thp.thehive.services.TaskSrv

import org.elastic4play.database.DBFind

@Singleton
class TaskMigration @Inject()(
    taskSrv: TaskSrv,
    userMigration: UserMigration,
    logMigration: LogMigration,
    auditMigration: AuditMigration,
    fromFind: DBFind,
    implicit val mat: Materializer
) extends Utils {

  implicit val taskReads: Reads[Task] =
    ((JsPath \ "title").read[String] and
      (JsPath \ "group").readNullable[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "status").readWithDefault[String]("completed").map(s ⇒ s(0).toLower + s.substring(1)).map(TaskStatus.withName) and
      (JsPath \ "flag").read[Boolean] and
      (JsPath \ "startDate").readNullable[Date] and
      (JsPath \ "endDate").readNullable[Date] and
      (JsPath \ "order").read[Int] and
      (JsPath \ "dueDate").readNullable[Date])(Task.apply _) // TODO add group:String in task

  def importTasks(caseId: String, `case`: Case with Entity, progress: ProgressBar)(implicit graph: Graph): Unit = {
    val done = fromFind(Some("all"), Nil)(
      index ⇒
        search(index / "case_task")
          .query(hasParentQuery("case", idsQuery(caseId), score = false))
    )._1
      .map { taskJs ⇒
        catchError("Task", taskJs, progress) {
          userMigration.withUser((taskJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
            val task = taskJs.as[Task]
//            logger.info(s"Importing task #${`case`.number} ${task.title}")
            taskSrv.create(task, `case`).foreach { taskEntity ⇒
              (taskJs \ "owner").asOpt[String].flatMap(userMigration.get).foreach(owner ⇒ taskSrv.assign(taskEntity, owner.user))
              auditMigration.importAudits("case_task", (taskJs \ "_id").as[String], taskEntity, progress)
              logMigration.importLogs((taskJs \ "_id").as[String], taskEntity, progress)
            }
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

}
