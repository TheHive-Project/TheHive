package org.thp.thehive.migration
import java.util.Date

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models.{Alert, AlertCase, Organisation}
import org.thp.thehive.services.AlertSrv

import org.elastic4play.database.DBFind

@Singleton
class AlertMigration @Inject()(
    alertSrv: AlertSrv,
    dbFind: DBFind,
    userMigration: UserMigration,
    caseTemplateMigration: CaseTemplateMigration,
    caseMigration: CaseMigration,
    auditMigration: AuditMigration,
    implicit val mat: Materializer
) extends Utils {

  implicit val alertReads: Reads[Alert] =
    ((JsPath \ "type").read[String] and
      (JsPath \ "source").read[String] and
      (JsPath \ "sourceRef").read[String] and
      (JsPath \ "title").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "severity").read[Int] and
      (JsPath \ "date").read[Date] and
      (JsPath \ "lastSyncDate").read[Date] and
      (JsPath \ "tags").readWithDefault[Set[String]](Set.empty) and
      (JsPath \ "flag").readWithDefault[Boolean](false) and
      (JsPath \ "tlp").read[Int] and
      Reads.pure(2) and                                                               // pap
      (JsPath \ "status").read[String].map(s ⇒ s == "Updated" || s == "Imported") and // read
      (JsPath \ "follow").read[Boolean])(Alert.apply _)

  def importAlerts(terminal: Terminal, organisation: Organisation with Entity)(implicit db: Database): Unit = {
    val (srv, total) = dbFind(Some("all"), Nil)(index ⇒ search(index / "alert"))
    val progress     = new ProgressBar(terminal, "Importing alert", Await.result(total, Duration.Inf).toInt)
    val done = srv
      .map { alertJs ⇒
        catchError("alert", alertJs, progress) {
          db.transaction { implicit graph ⇒
            userMigration.withUser((alertJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
              val alert = alertJs.as[Alert]
              progress.inc(extraMessage = alert.title)
              val caseTemplate = (alertJs \ "caseTemplate").asOpt[String].flatMap(caseTemplateMigration.get)
              val customFields = (alertJs \ "customFields")
                .asOpt[JsObject]
                .fold(Seq.empty[(String, Option[Any])])(extractCustomFields)
                .toMap
              alertSrv.create(alert, organisation, customFields, caseTemplate).map { richAlert ⇒
                (alertJs \ "caze")
                  .asOpt[String]
                  .flatMap(caseMigration.get)
                  .foreach(caze ⇒ alertSrv.alertCaseSrv.create(AlertCase(), richAlert.alert, caze))
                auditMigration.importAudits("alert", (alertJs \ "_id").as[String], richAlert.alert, progress)
              }
            }
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }
}
