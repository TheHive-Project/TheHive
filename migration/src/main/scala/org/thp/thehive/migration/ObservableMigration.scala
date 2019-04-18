package org.thp.thehive.migration
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{hasParentQuery, idsQuery, search, RichString}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.Hasher
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, StorageSrv}
import org.thp.thehive.models._
import org.thp.thehive.services.{AttachmentSrv, DataSrv, ObservableSrv}

import org.elastic4play.database.DBFind
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services.{Attachment ⇒ ElasticAttachment, AttachmentSrv ⇒ ElasticAttachmentSrv}

@Singleton
class ObservableMigration @Inject()(
    config: Configuration,
    implicit val toDB: Database,
    observableSrv: ObservableSrv,
    userMigration: UserMigration,
    storageSrv: StorageSrv,
    attachmentSrv: AttachmentSrv,
    auditMigration: AuditMigration,
    dataSrv: DataSrv,
    elasticAttachmentSrv: ElasticAttachmentSrv,
    fromFind: DBFind,
    implicit val mat: Materializer)
    extends Utils {

  val observableDataSrv       = new EdgeSrv[ObservableData, Observable, Data]
  val observableAttachmentSrv = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val caseObservableSrv       = new EdgeSrv[CaseObservable, Case, Observable]
  val hashers                 = Hasher(config.get[Seq[String]]("attachment.hash"): _*)

  implicit val artifactReads: Reads[Observable] =
    ((JsPath \ "dataType").read[String] and
      (JsPath \ "tags").readWithDefault[Seq[String]](Nil) and
      (JsPath \ "message").readNullable[String] and
      (JsPath \ "tlp").readWithDefault[Int](2) and
      (JsPath \ "ioc").readWithDefault[Boolean](false) and
      (JsPath \ "sighted").readWithDefault[Boolean](false))(Observable.apply _)

  def importObservables(caseId: String, `case`: Case with Entity, progress: ProgressBar)(implicit graph: Graph): Unit = {
    val done = fromFind(Some("all"), Nil)(
      index ⇒
        search(index / "case_artifact")
          .query(hasParentQuery("case", idsQuery(caseId), score = false)))._1
      .map { artifactJs ⇒
        catchError("Artifact", artifactJs, progress) {
          userMigration.withUser((artifactJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
            val observable        = artifactJs.as[Observable]
            val createdObservable = observableSrv.create(observable)
            (artifactJs \ "attachment")
              .asOpt[ElasticAttachment]
              .foreach { attachment ⇒
                val attachmentEntity = saveAttachment(attachmentSrv, storageSrv, elasticAttachmentSrv, hashers, toDB)(attachment)
                observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachmentEntity)
              }
            (artifactJs \ "data")
              .asOpt[String]
              .foreach { data ⇒
                val dataEntity = dataSrv.create(Data(data))
                observableDataSrv.create(ObservableData(), createdObservable, dataEntity)
              }
            caseObservableSrv.create(CaseObservable(), `case`, createdObservable)
            auditMigration.importAudits("case_artifact", (artifactJs \ "_id").as[String], createdObservable, progress)
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }

}
