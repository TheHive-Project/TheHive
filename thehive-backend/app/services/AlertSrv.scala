package services

import java.nio.file.Files
import javax.inject.Inject

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.ConnectorRouter
import models._
import org.elastic4play.InternalError
import org.elastic4play.controllers.{ Fields, FileInputValue }
import org.elastic4play.services._
import org.elastic4play.utils.Hasher
import play.api.libs.json._
import play.api.{ Configuration, Logger }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

trait AlertTransformer {
  def createCase(alert: Alert)(implicit authContext: AuthContext): Future[Case]
}

case class CaseSimilarity(caze: Case, similarIOCCount: Int, iocCount: Int, similarArtifactCount: Int, artifactCount: Int)

class AlertSrv(
    templates: Map[String, String],
    alertModel: AlertModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    caseTemplateSrv: CaseTemplateSrv,
    attachmentSrv: AttachmentSrv,
    connectors: ConnectorRouter,
    hashAlg: Seq[String],
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AlertTransformer {

  @Inject() def this(
    configuration: Configuration,
    alertModel: AlertModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    caseSrv: CaseSrv,
    artifactSrv: ArtifactSrv,
    caseTemplateSrv: CaseTemplateSrv,
    attachmentSrv: AttachmentSrv,
    connectors: ConnectorRouter,
    ec: ExecutionContext,
    mat: Materializer) = this(
    Map.empty[String, String],
    alertModel: AlertModel,
    createSrv,
    getSrv,
    updateSrv,
    deleteSrv,
    findSrv,
    caseSrv,
    artifactSrv,
    caseTemplateSrv,
    attachmentSrv,
    connectors,
    (configuration.getString("datastore.hash.main").get +: configuration.getStringSeq("datastore.hash.extra").get).distinct,
    ec,
    mat)

  private[AlertSrv] lazy val logger = Logger(getClass)

  def create(fields: Fields)(implicit authContext: AuthContext): Future[Alert] =
    createSrv[AlertModel, Alert](alertModel, fields)

  def bulkCreate(fieldSet: Seq[Fields])(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    createSrv[AlertModel, Alert](alertModel, fieldSet)

  def get(id: String): Future[Alert] =
    getSrv[AlertModel, Alert](alertModel, id)

  def get(tpe: String, source: String, sourceRef: String): Future[Option[Alert]] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[AlertModel, Alert](
      alertModel,
      and("type" ~= tpe, "source" ~= source, "sourceRef" ~= sourceRef),
      Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
  }

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Alert] =
    updateSrv[AlertModel, Alert](alertModel, id, fields)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] = {
    updateSrv[AlertModel, Alert](alertModel, ids, fields)
  }

  def bulkUpdate(updates: Seq[(Alert, Fields)])(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    updateSrv[Alert](updates)

  def markAsRead(alert: Alert)(implicit authContext: AuthContext): Future[Alert] = {
    alert.caze() match {
      case Some(_) ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Imported"))
      case None    ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Ignored"))
    }
  }

  def markAsUnread(alert: Alert)(implicit authContext: AuthContext): Future[Alert] = {
    alert.caze() match {
      case Some(_) ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Updated"))
      case None    ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "New"))
    }
  }

  def getCaseTemplate(alert: Alert): Future[Option[CaseTemplate]] = {
    val templateName = alert.caseTemplate()
      .orElse(templates.get(alert.tpe()))
      .getOrElse(alert.tpe())
    caseTemplateSrv.getByName(templateName)
      .map { ct ⇒ Some(ct) }
      .recover { case _ ⇒ None }
  }

  private val dataExtractor = "^(.*);(.*);(.*)".r

  def createCase(alert: Alert)(implicit authContext: AuthContext): Future[Case] = {
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        connectors.get(alert.tpe()) match {
          case Some(connector: AlertTransformer) ⇒ connector.createCase(alert)
          case _ ⇒
            getCaseTemplate(alert).flatMap { caseTemplate ⇒
              println(s"Create case using template $caseTemplate")
              caseSrv.create(
                Fields.empty
                  .set("title", s"#${alert.sourceRef()} " + alert.title())
                  .set("description", alert.description())
                  .set("severity", JsNumber(alert.severity()))
                  .set("tags", JsArray(alert.tags().map(JsString)))
                  .set("tlp", JsNumber(alert.tlp()))
                  .set("status", CaseStatus.Open.toString),
                caseTemplate)
                .flatMap { caze ⇒ setCase(alert, caze).map(_ ⇒ caze) }
                .flatMap { caze ⇒
                  val artifactsFields = alert.artifacts()
                    .map { artifact ⇒
                      val tags = (artifact \ "tags").asOpt[Seq[JsString]].getOrElse(Nil) :+ JsString("src:" + alert.tpe())
                      val message = (artifact \ "message").asOpt[JsString].getOrElse(JsString(""))
                      val artifactFields = Fields(artifact +
                        ("tags" → JsArray(tags)) +
                        ("message" → message))
                      if (artifactFields.getString("dataType").contains("file")) {
                        artifactFields.getString("data")
                          .map {
                            case dataExtractor(filename, contentType, data) ⇒
                              val f = Files.createTempFile("alert-", "-attachment")
                              Files.write(f, java.util.Base64.getDecoder.decode(data))
                              artifactFields
                                .set("attachment", FileInputValue(filename, f, contentType))
                                .unset("data")
                            case data ⇒
                              logger.warn(s"Invalid data format for file artifact: $data")
                              artifactFields
                          }
                          .getOrElse(artifactFields)
                      }
                      else {
                        artifactFields
                      }
                    }

                  val createdCase = artifactSrv.create(caze, artifactsFields)
                    .map { r ⇒
                      r.foreach {
                        case Failure(e) ⇒ logger.warn("Create artifact error", e)
                        case _          ⇒
                      }
                      caze
                    }
                  createdCase.onComplete { _ ⇒
                    // remove temporary files
                    artifactsFields
                      .flatMap(_.get("Attachment"))
                      .foreach {
                        case FileInputValue(_, file, _) ⇒ Files.delete(file)
                        case _                          ⇒
                      }
                  }
                  createdCase
                }
            }
        }
    }
  }

  def setCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Alert] = {
    updateSrv(alert, Fields(Json.obj("case" → caze.id, "status" → AlertStatus.Imported)))
  }

  def delete(id: String)(implicit Context: AuthContext): Future[Alert] =
    deleteSrv[AlertModel, Alert](alertModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Alert, NotUsed], Future[Long]) = {
    findSrv[AlertModel, Alert](alertModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(alertModel, queryDef, aggs: _*)

  def setFollowAlert(alertId: String, follow: Boolean)(implicit authContext: AuthContext): Future[Alert] = {
    updateSrv[AlertModel, Alert](alertModel, alertId, Fields(Json.obj("follow" → follow)))
  }

  def similarCases(alert: Alert): Future[Seq[CaseSimilarity]] = {
    def similarArtifacts(artifact: JsObject): Option[Source[Artifact, NotUsed]] = {
      for {
        dataType ← (artifact \ "dataType").asOpt[String]
        d ← (artifact \ "data").asOpt[String]
        data ← (dataType, d) match {
          case ("file", dataExtractor(filename, contentType, b64content)) ⇒
            val content = java.util.Base64.getDecoder.decode(b64content)
            val hashes = Hasher(hashAlg: _*).fromByteArray(content)
            Some(Right(Attachment(filename, hashes, content.length.toLong, contentType, "")))
          case ("file", _) ⇒
            logger.warn(s"Invalid data format for file artifact: $d")
            None
          case _ ⇒
            Some(Left(d))
        }
      } yield artifactSrv.findSimilar(dataType, data, None, Some("all"), Nil)._1
    }

    def getCaseAndArtifactCount(caseId: String): Future[(Case, Int, Int)] = {
      import org.elastic4play.services.QueryDSL._
      for {
        caze ← caseSrv.get(caseId)
        artifactCountJs ← artifactSrv.stats(parent("case", withId(caseId)), Seq(groupByField("ioc", selectCount)))
        iocCount = (artifactCountJs \ "1" \ "count").asOpt[Int].getOrElse(0)
        artifactCount = (artifactCountJs \\ "count").map(_.as[Int]).sum
      } yield (caze, iocCount, artifactCount)
    }

    Source(alert.artifacts().to[immutable.Iterable])
      .flatMapConcat { artifact ⇒
        similarArtifacts(artifact)
          .getOrElse(Source.empty)
      }
      .groupBy(100, _.parentId)
      .map {
        case a if a.ioc() ⇒ (a.parentId, 1, 1)
        case a            ⇒ (a.parentId, 0, 1)
      }
      .reduce[(Option[String], Int, Int)] {
        case ((caze, iocCount1, artifactCount1), (_, iocCount2, artifactCount2)) ⇒ (caze, iocCount1 + iocCount2, artifactCount1 + artifactCount2)
      }
      .mergeSubstreams
      .mapAsyncUnordered(5) {
        case (Some(caseId), similarIOCCount, similarArtifactCount) ⇒
          getCaseAndArtifactCount(caseId).map {
            case (caze, iocCount, artifactCount) ⇒ CaseSimilarity(caze, similarIOCCount, iocCount, similarArtifactCount, artifactCount)
          }
        case _ ⇒ Future.failed(InternalError("Case not found"))
      }
      .runWith(Sink.seq)
  }
}