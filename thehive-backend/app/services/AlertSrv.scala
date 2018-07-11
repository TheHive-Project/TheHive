package services

import java.nio.file.Files
import javax.inject.{ Inject, Singleton }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex
import scala.util.{ Failure, Try }

import play.api.libs.json._
import play.api.{ Configuration, Logger }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import connectors.ConnectorRouter
import models._

import org.elastic4play.InternalError
import org.elastic4play.controllers.{ Fields, FileInputValue }
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services.JsonFormat.attachmentFormat
import org.elastic4play.services.QueryDSL.{ groupByField, parent, selectCount, withId }
import org.elastic4play.services._

trait AlertTransformer {
  def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext): Future[Case]

  def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case]
}

case class CaseSimilarity(caze: Case, similarIOCCount: Int, iocCount: Int, similarArtifactCount: Int, artifactCount: Int)

object AlertSrv {
  val dataExtractor: Regex = "^(.*);(.*);(.*)".r
}

@Singleton
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
    (configuration.get[String]("datastore.hash.main") +: configuration.get[Seq[String]]("datastore.hash.extra")).distinct,
    ec,
    mat)

  private[AlertSrv] lazy val logger = Logger(getClass)

  import AlertSrv._

  def create(fields: Fields)(implicit authContext: AuthContext): Future[Alert] = {

    val artifactsFields =
      Future.traverse(fields.getValues("artifacts")) {
        case a: JsObject if (a \ "dataType").asOpt[String].contains("file") ⇒
          (a \ "data").asOpt[String] match {
            case Some(dataExtractor(filename, contentType, data)) ⇒
              attachmentSrv.save(filename, contentType, java.util.Base64.getDecoder.decode(data))
                .map(attachment ⇒ a - "data" + ("attachment" → Json.toJson(attachment)))
            case _ ⇒ Future.successful(a)
          }
        case a ⇒ Future.successful(a)
      }
    artifactsFields.flatMap { af ⇒
      createSrv[AlertModel, Alert](alertModel, fields.set("artifacts", JsArray(af)))
    }
  }

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
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Alert] =
    updateSrv[AlertModel, Alert](alertModel, id, fields, modifyConfig)

  def update(alert: Alert, fields: Fields)(implicit authContext: AuthContext): Future[Alert] =
    update(alert, fields, ModifyConfig.default)

  def update(alert: Alert, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Alert] =
    updateSrv(alert, fields, modifyConfig)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    bulkUpdate(ids, fields, ModifyConfig.default)

  def bulkUpdate(ids: Seq[String], fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    updateSrv[AlertModel, Alert](alertModel, ids, fields, modifyConfig)

  def bulkUpdate(updates: Seq[(Alert, Fields)])(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    bulkUpdate(updates, ModifyConfig.default)

  def bulkUpdate(updates: Seq[(Alert, Fields)], modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Seq[Try[Alert]]] =
    updateSrv[Alert](updates, modifyConfig)

  def markAsRead(alert: Alert, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Alert] = {
    alert.caze() match {
      case Some(_) ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Imported"), modifyConfig)
      case None    ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Ignored"), modifyConfig)
    }
  }

  def markAsUnread(alert: Alert, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Alert] = {
    alert.caze() match {
      case Some(_) ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "Updated"), modifyConfig)
      case None    ⇒ updateSrv[AlertModel, Alert](alertModel, alert.id, Fields.empty.set("status", "New"), modifyConfig)
    }
  }

  def getCaseTemplate(alert: Alert, customCaseTemplate: Option[String]): Future[Option[CaseTemplate]] = {
    val templateName = customCaseTemplate
      .orElse(alert.caseTemplate())
      .orElse(templates.get(alert.tpe()))
      .getOrElse(alert.tpe())
    caseTemplateSrv.getByName(templateName)
      .map { ct ⇒ Some(ct) }
      .recover { case _ ⇒ None }
  }

  def createCase(alert: Alert, customCaseTemplate: Option[String])(implicit authContext: AuthContext): Future[Case] = {
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        connectors.get(alert.tpe()) match {
          case Some(connector: AlertTransformer) ⇒
            for {
              caze ← connector.createCase(alert, customCaseTemplate)
              _ ← setCase(alert, caze)
            } yield caze
          case _ ⇒
            for {
              caseTemplate ← getCaseTemplate(alert, customCaseTemplate)
              caze ← caseSrv.create(
                Fields.empty
                  .set("title", alert.title())
                  .set("description", alert.description())
                  .set("severity", JsNumber(alert.severity()))
                  .set("tags", JsArray(alert.tags().map(JsString)))
                  .set("tlp", JsNumber(alert.tlp()))
                  .set("status", CaseStatus.Open.toString)
                  .set("startDate", Json.toJson(alert.date())),
                caseTemplate)
              _ ← importArtifacts(alert, caze)
              _ ← setCase(alert, caze)
            } yield caze
        }
    }
  }

  override def mergeWithCase(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] = {
    alert.caze() match {
      case Some(id) ⇒ caseSrv.get(id)
      case None ⇒
        connectors.get(alert.tpe()) match {
          case Some(connector: AlertTransformer) ⇒
            for {
              updatedCase ← connector.mergeWithCase(alert, caze)
              _ ← setCase(alert, updatedCase)
            } yield updatedCase
          case _ ⇒
            for {
              _ ← importArtifacts(alert, caze)
              description = caze.description() + s"\n  \n#### Merged with alert #${alert.sourceRef()} ${alert.title()}\n\n${alert.description().trim}"
              updatedCase ← caseSrv.update(caze, Fields.empty.set("description", description))
              _ ← setCase(alert, caze)
            } yield updatedCase
        }
    }
  }

  def importArtifacts(alert: Alert, caze: Case)(implicit authContext: AuthContext): Future[Case] = {
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

    val updatedCase = artifactSrv.create(caze, artifactsFields)
      .map { artifacts ⇒
        artifacts.collect {
          case Failure(e) ⇒ logger.warn("Create artifact error", e)
        }
        caze
      }
    updatedCase.onComplete { _ ⇒
      // remove temporary files
      artifactsFields
        .flatMap(_.get("Attachment"))
        .foreach {
          case FileInputValue(_, file, _) ⇒ Files.delete(file)
          case _                          ⇒
        }
    }
    updatedCase
  }

  def setCase(alert: Alert, caze: Case, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Alert] = {
    updateSrv(alert, Fields(Json.obj("case" → caze.id, "status" → AlertStatus.Imported)), modifyConfig)
  }

  def unsetCase(alert: Alert, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Alert] = {
    val status = alert.status match {
      case AlertStatus.New      ⇒ AlertStatus.New
      case AlertStatus.Updated  ⇒ AlertStatus.New
      case AlertStatus.Ignored  ⇒ AlertStatus.Ignored
      case AlertStatus.Imported ⇒ AlertStatus.Ignored
    }
    logger.debug(s"Remove case association in alert ${alert.id} (${alert.title}")
    updateSrv(alert, Fields(Json.obj("case" → JsNull, "status" → status)), modifyConfig)
  }

  def delete(id: String)(implicit authContext: AuthContext): Future[Alert] =
    deleteSrv[AlertModel, Alert](alertModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Alert, NotUsed], Future[Long]) = {
    findSrv[AlertModel, Alert](alertModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(alertModel, queryDef, aggs: _*)

  def setFollowAlert(alertId: String, follow: Boolean, modifyConfig: ModifyConfig = ModifyConfig.default)(implicit authContext: AuthContext): Future[Alert] = {
    updateSrv[AlertModel, Alert](alertModel, alertId, Fields(Json.obj("follow" → follow)), modifyConfig)
  }

  def similarCases(alert: Alert): Future[Seq[CaseSimilarity]] = {
    def similarArtifacts(artifact: JsObject): Option[Source[Artifact, NotUsed]] = {
      for {
        dataType ← (artifact \ "dataType").asOpt[String]
        data ← if (dataType == "file")
          (artifact \ "attachment").asOpt[Attachment].map(Right.apply)
        else
          (artifact \ "data").asOpt[String].map(Left.apply)
      } yield artifactSrv.findSimilar(dataType, data, None, Some("all"), Nil)._1
    }

    Source(alert.artifacts().to[immutable.Iterable])
      .flatMapConcat { artifact ⇒
        similarArtifacts(artifact)
          .getOrElse(Source.empty)
      }
      .fold(Map.empty[String, (Int, Int)]) { (similarCases, artifact) ⇒
        val caseId = artifact.parentId.getOrElse(sys.error(s"Artifact ${artifact.id} has no case !"))
        val (iocCount, artifactCount) = similarCases.getOrElse(caseId, (0, 0))
        if (artifact.ioc())
          similarCases + (caseId → ((iocCount + 1, artifactCount)))
        else
          similarCases + (caseId → ((iocCount, artifactCount + 1)))
      }
      .mapConcat(identity)
      .mapAsyncUnordered(5) {
        case (caseId, (similarIOCCount, similarArtifactCount)) ⇒
          caseSrv.get(caseId).map((_, similarIOCCount, similarArtifactCount))
      }
      .filter {
        case (caze, _, _) ⇒ caze.status() != CaseStatus.Deleted && !caze.resolutionStatus().contains(CaseResolutionStatus.Duplicated)
      }
      .mapAsyncUnordered(5) {
        case (caze, similarIOCCount, similarArtifactCount) ⇒
          for {
            artifactCountJs ← artifactSrv.stats(parent("case", withId(caze.id)), Seq(groupByField("ioc", selectCount)))
            iocCount = (artifactCountJs \ "1" \ "count").asOpt[Int].getOrElse(0)
            artifactCount = (artifactCountJs \\ "count").map(_.as[Int]).sum
          } yield CaseSimilarity(caze, similarIOCCount, iocCount, similarArtifactCount, artifactCount)
        case _ ⇒ Future.failed(InternalError("Case not found"))
      }
      .runWith(Sink.seq)
  }

  def getArtifactSeen(artifact: JsObject): Future[Long] = {
    val maybeArtifactSeen = for {
      dataType ← (artifact \ "dataType").asOpt[String]
      data ← dataType match {
        case "file" ⇒ (artifact \ "attachment").asOpt[Attachment].map(Right.apply)
        case _      ⇒ (artifact \ "data").asOpt[String].map(Left.apply)
      }
      numberOfSimilarArtifacts = artifactSrv.findSimilar(dataType, data, None, None, Nil)._2
    } yield numberOfSimilarArtifacts
    maybeArtifactSeen.getOrElse(Future.successful(0L))
  }

  def alertArtifactsWithSeen(alert: Alert): Future[Seq[JsObject]] = {
    Future.traverse(alert.artifacts()) { artifact ⇒
      getArtifactSeen(artifact).map(seen ⇒ artifact + ("seen" → JsNumber(seen)))
    }
  }

  def fixStatus()(implicit authContext: AuthContext): Future[Unit] = {
    import org.elastic4play.services.QueryDSL._

    val updatedStatusFields = Fields.empty.set("status", "Updated")
    val (updateAlerts, updateAlertCount) = find("status" ~= "Update", Some("all"), Nil)
    updateAlertCount.foreach(c ⇒ logger.info(s"Updating $c alert with Update status"))
    val updateAlertProcess = updateAlerts
      .mapAsyncUnordered(3) { alert ⇒
        logger.debug(s"Updating alert ${alert.id} (status: Update → Updated)")
        update(alert, updatedStatusFields)
          .andThen {
            case Failure(error) ⇒ logger.warn(s"""Fail to set "Updated" status to alert ${alert.id}""", error)
          }
      }

    val ignoredStatusFields = Fields.empty.set("status", "Ignored")
    val (ignoreAlerts, ignoreAlertCount) = find("status" ~= "Ignore", Some("all"), Nil)
    ignoreAlertCount.foreach(c ⇒ logger.info(s"Updating $c alert with Ignore status"))
    val ignoreAlertProcess = ignoreAlerts
      .mapAsyncUnordered(3) { alert ⇒
        logger.debug(s"Updating alert ${alert.id} (status: Ignore → Ignored)")
        update(alert, ignoredStatusFields)
          .andThen {
            case Failure(error) ⇒ logger.warn(s"""Fail to set "Ignored" status to alert ${alert.id}""", error)
          }
      }

    (updateAlertProcess ++ ignoreAlertProcess)
      .runWith(Sink.ignore)
      .map(_ ⇒ ())
  }
}