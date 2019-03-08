package org.thp.thehive.migration
import java.util.Date

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseTemplateSrv, CustomFieldSrv, TaskSrv}

import org.elastic4play.database.DBFind

@Singleton
class CaseTemplateMigration @Inject()(
    dbFind: DBFind,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    customFieldSrv: CustomFieldSrv,
    auditMigration: AuditMigration,
    implicit val mat: Materializer)
    extends Utils {
  private var caseTemplateMap: Map[String, RichCaseTemplate] = Map.empty[String, RichCaseTemplate]

  implicit val caseTemplateReads: Reads[CaseTemplate] =
    ((JsPath \ "name").read[String] and
      (JsPath \ "titlePrefix").readNullable[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "severity").readNullable[Int] and
      (JsPath \ "tags").readWithDefault[Seq[String]](Nil) and
      (JsPath \ "flag").readWithDefault[Boolean](false) and
      (JsPath \ "tlp").readNullable[Int] and
      Reads.pure(None) and // pap
      Reads.pure(None))(CaseTemplate.apply _)

  implicit val taskReads: Reads[Task] =
    ((JsPath \ "title").read[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "status").read[String].map(s ⇒ s(0).toLower + s.substring(1)).map(TaskStatus.withName) and
      (JsPath \ "flag").read[Boolean] and
      (JsPath \ "startDate").readNullable[Date] and
      (JsPath \ "endDate").readNullable[Date] and
      (JsPath \ "order").read[Int] and
      (JsPath \ "dueDate").readNullable[Date])(Task.apply _) // TODO add group:String in task

  def importCaseTemplateTask(caseTemplateTaskJs: JsObject, caseTemplate: CaseTemplate with Entity, progress: ProgressBar)(
      implicit graph: Graph,
      authContext: AuthContext): Unit =
    catchError("caseTemplateTask", caseTemplateTaskJs, progress) {
      caseTemplateTaskJs.as[Seq[Task]].foreach { task ⇒
        taskSrv.create(task, caseTemplate)
      }
    }

  def importCaseTemplates(terminal: Terminal, organisation: Organisation with Entity)(implicit db: Database, authContext: AuthContext): Unit = {
    val (srv, total) = dbFind(Some("all"), Nil)(index ⇒ search(index / "caseTemplate"))
    val progress     = new ProgressBar(terminal, "Importing case template", Await.result(total, Duration.Inf).toInt)
    val done = srv
      .map { caseTemplateJs ⇒
        catchError("caseTemplate", caseTemplateJs, progress) {
          db.transaction { implicit graph ⇒
            progress.inc(extraMessage = (caseTemplateJs \ "name").asOpt[String].getOrElse("***"))
            val caseTemplate = caseTemplateJs.as[CaseTemplate]
            val customFields = (caseTemplateJs \ "customFields")
              .asOpt[JsObject]
              .fold(Seq.empty[(String, Option[Any])])(extractCustomFields) ++
              (caseTemplateJs \ "metrics")
                .asOpt[JsObject]
                .fold(Seq.empty[(String, Option[Any])])(extractMetrics)

            val richCaseTemplate = caseTemplateSrv.create(caseTemplate, organisation, customFields)
            caseTemplateMap += caseTemplate.name → richCaseTemplate
            (caseTemplateJs \ "tasks").asOpt[JsObject].foreach(importCaseTemplateTask(_, richCaseTemplate.caseTemplate, progress))
            auditMigration.importAudits("caseTemplate", (caseTemplateJs \ "_id").as[String], richCaseTemplate.caseTemplate, progress)
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

  def get(name: String): Option[RichCaseTemplate] = caseTemplateMap.get(name)
}
