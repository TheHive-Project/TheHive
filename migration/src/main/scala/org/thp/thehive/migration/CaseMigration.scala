package org.thp.thehive.migration
import java.io.IOException
import java.util.Date

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsObject, JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{search, RichString}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Retry
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.thehive.models.{Case, CaseStatus, Organisation}
import org.thp.thehive.services.{CaseSrv, ImpactStatusSrv, ResolutionStatusSrv}

import org.elastic4play.database.DBFind

@Singleton
class CaseMigration @Inject()(
    caseSrv: CaseSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    userMigration: UserMigration,
    taskMigration: TaskMigration,
    observableMigration: ObservableMigration,
    auditMigration: AuditMigration,
    dbFind: DBFind,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends Utils {

  private var caseMap: Map[String, String] = Map.empty[String, String]

  implicit val caseReads: Reads[Case] =
    ((JsPath \ "caseId").read[Int] and
      (JsPath \ "title").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "severity").read[Int] and
      (JsPath \ "startDate").read[Date] and
      (JsPath \ "endDate").readNullable[Date] and
      (JsPath \ "tags").readWithDefault[Set[String]](Set.empty) and
      (JsPath \ "flag").read[Boolean] and
      (JsPath \ "tlp").read[Int] and
      (JsPath \ "pap").read[Int] and
      (JsPath \ "status").read[String].map(_.toLowerCase).map(CaseStatus.withName) and
      (JsPath \ "summary").readNullable[String])(Case.apply _)

  def importCases(terminal: Terminal, organisation: Organisation with Entity)(implicit db: Database): Unit = {
    // TODO add mergeFrom, mergeTo
    val (srv, total) = dbFind(Some("all"), Nil)(index ⇒ search(index / "case"))
    val progress     = new ProgressBar(terminal, "Importing case", Await.result(total, Duration.Inf).toInt)
    val done = srv
      .mapAsync(10) { caseJs ⇒
        val caze = caseJs.as[Case]
        progress.inc(extraMessage = s"#${caze.number} ${caze.title}")
        Future {
          val t1 = System.currentTimeMillis()
          var t2 = 0L
          catchError("case", caseJs, progress) {
            Retry(3, classOf[IOException]) {
              db.tryTransaction { implicit graph ⇒
                val r = userMigration.withUser((caseJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
                  val customFields = ((caseJs \ "customFields")
                    .asOpt[JsObject]
                    .fold(Seq.empty[(String, Option[Any])])(extractCustomFields) ++
                    (caseJs \ "metrics")
                      .asOpt[JsObject]
                      .fold(Seq.empty[(String, Option[Any])])(extractMetrics)).toMap
                  caseSrv
                    .create(
                      caze,
                      (caseJs \ "owner").asOpt[String].flatMap(userMigration.get).map(_.user),
                      organisation,
                      customFields,
                      None // no case template
                    )
                    .map { richCase ⇒
                      (caseJs \ "impactStatus")
                        .asOpt[String]
                        .flatMap(impactStatusSrv.get(_).headOption())
                        .foreach(caseSrv.setImpactStatus(richCase.`case`, _))
                      (caseJs \ "resolutionStatus")
                        .asOpt[String]
                        .flatMap(resolutionStatusSrv.get(_).headOption())
                        .foreach(caseSrv.setResolutionStatus(richCase.`case`, _))
                      val caseId = (caseJs \ "_id").as[String]
                      caseMap += caseId → richCase._id
                      auditMigration.importAudits("case", caseId, richCase.`case`, progress)
                      taskMigration.importTasks(caseId, richCase.`case`, progress)
                      observableMigration.importObservables(caseId, richCase.`case`, progress)
                    }
                }
                t2 = System.currentTimeMillis()
                r
              }
            }.get
          }
          val t3 = System.currentTimeMillis()
          logger.info(s"Timing : ${t3 - t1}ms / ${t3 - t2}ms")
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

  def get(id: String)(implicit graph: Graph): Option[Case with Entity] = caseMap.get(id).flatMap(caseSrv.get(_).headOption())
}
