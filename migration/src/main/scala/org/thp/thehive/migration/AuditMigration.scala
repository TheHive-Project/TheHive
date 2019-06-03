package org.thp.thehive.migration
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl.{boolQuery, search, termQuery, RichString}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.elastic4play.database.DBFind
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.EdgeSrv
import org.thp.thehive.models.{Audit, Audited}
import org.thp.thehive.services.{AuditSrv, EventSrv}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@Singleton
class AuditMigration @Inject()(
    auditSrv: AuditSrv,
    userMigration: UserMigration,
    dbFind: DBFind,
    implicit val db: Database,
    implicit val mat: Materializer
) extends Utils {

  lazy val edgeSrv: EdgeSrv[Audited, Audit, Product] = new EdgeSrv[Audited, Audit, Product]

  def create(audit: Audit, entity: Entity)(implicit graph: Graph, authContext: AuthContext): Unit = {
    val createdAudit = auditSrv.create(audit)
    edgeSrv.create(Audited(), createdAudit, entity)
    ()
  }

  def auditReads(attribute: Option[String] = None, newValue: Option[String] = None): Reads[Audit] =
    ((JsPath \ "operation").read[String] /*.map(AuditableAction.withName)*/ and // FIXME map operation to action
      (JsPath \ "requestId").read[String] and
      Reads.pure(attribute) and
      Reads.pure(None) and
      Reads.pure(newValue))(Audit.apply _)

  implicit val multiAuditReads: Reads[Seq[Audit]] = Reads { json ⇒
    (json \ "details")
      .validateOpt[JsObject]
      .map(_.getOrElse(JsObject.empty))
      .flatMap { details ⇒
        if (details.fields.isEmpty)
          auditReads().reads(json).map(Seq(_))
        else {
          type Errors = Seq[(JsPath, Seq[JsonValidationError])]
          details
            .fields
            .foldLeft[Either[Errors, Seq[Audit]]](Right(Nil)) {
              case (acc, (attribute, newValue)) ⇒
                (acc, auditReads(Some(attribute), Some(newValue.toString)).reads(json)) match {
                  case (Right(a), JsSuccess(x, _))           ⇒ Right(a :+ x)
                  case (_: Right[_, _], JsError(error))      ⇒ Left(error)
                  case (errors: Left[_, _], JsSuccess(_, _)) ⇒ errors
                  case (Left(errors), JsError(error))        ⇒ Left(errors ++ error)
                }
            }
            .fold(JsError.apply, JsSuccess(_))
        }
      }
  }

  def importAudits(objectType: String, objectId: String, entity: Entity, progress: ProgressBar)(implicit graph: Graph): Unit = {
    val done = dbFind(Some("all"), Nil)(
      index ⇒ search(index / "audit").query(boolQuery().must(termQuery("objectType", objectType), termQuery("objectId", objectId)))
    )._1
      .map { auditJs ⇒
        catchError("audit", auditJs, progress) {
          userMigration.withUser((auditJs \ "createdBy").asOpt[String].getOrElse("init")) { implicit authContext ⇒
            auditJs.as[Seq[Audit]].foreach { audit ⇒
              val createdAudit = auditSrv.create(audit)
              edgeSrv.create(Audited(), createdAudit, entity)
            }
          }
        }
      }
      .runWith(Sink.ignore)
    Await.ready(done, Duration.Inf)
    ()
  }
}
