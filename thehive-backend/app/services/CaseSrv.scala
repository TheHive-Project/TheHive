package services

import javax.inject.{Inject, Provider, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import models._

import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

@Singleton
class CaseSrv @Inject()(
    caseModel: CaseModel,
    artifactModel: ArtifactModel,
    taskSrv: TaskSrv,
    auditSrv: AuditSrv,
    alertSrvProvider: Provider[AlertSrv],
    createSrv: CreateSrv,
    artifactSrv: ArtifactSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val mat: Materializer
) {

  private lazy val alertSrv        = alertSrvProvider.get
  private[CaseSrv] lazy val logger = Logger(getClass)

  def applyTemplate(template: CaseTemplate, originalFields: Fields): Fields = {
    def getJsObjectOrEmpty(value: Option[JsValue]) = value.fold(JsObject.empty) {
      case obj: JsObject => obj
      case _             => JsObject.empty
    }

    val metrics      = originalFields.getValue("metrics").fold(JsObject.empty)(_.as[JsObject]) deepMerge template.metrics().as[JsObject]
    val tags         = (originalFields.getStrings("tags").getOrElse(Nil) ++ template.tags()).distinct
    val customFields = getJsObjectOrEmpty(template.customFields()) ++ getJsObjectOrEmpty(originalFields.getValue("customFields"))

    originalFields
      .set("title", originalFields.getString("title").map(t => JsString(template.titlePrefix().getOrElse("") + " " + t)))
      .set("description", originalFields.getString("description").orElse(template.description()).map(JsString))
      .set("severity", originalFields.getLong("severity").orElse(template.severity()).map(JsNumber(_)))
      .set("tags", JsArray(tags.map(JsString)))
      .set("flag", originalFields.getBoolean("flag").orElse(template.flag()).map(JsBoolean))
      .set("tlp", originalFields.getLong("tlp").orElse(template.tlp()).map(JsNumber(_)))
      .set("metrics", originalFields.getValue("metrics").flatMap(_.asOpt[JsObject]).getOrElse(JsObject.empty) ++ metrics)
      .set("customFields", customFields)
  }

  def create(fields: Fields, template: Option[CaseTemplate] = None)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] = {
    val fieldsWithOwner = fields.getString("owner") match {
      case None    => fields.set("owner", authContext.userId)
      case Some(_) => fields
    }
    val templatedCaseFields = template match {
      case None    => fieldsWithOwner
      case Some(t) => applyTemplate(t, fieldsWithOwner)
    }
    createSrv[CaseModel, Case](caseModel, templatedCaseFields.unset("tasks"))
      .flatMap { caze =>
        val taskFields = fields.getValues("tasks").collect {
          case task: JsObject => Fields(task)
        } ++ template.map(_.tasks().map(Fields(_))).getOrElse(Nil)
        taskSrv
          .create(caze, taskFields)
          .map(_ => caze)
      }
  }

  def get(id: String)(implicit ec: ExecutionContext): Future[Case] =
    getSrv[CaseModel, Case](caseModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    updateSrv[CaseModel, Case](caseModel, id, fields, modifyConfig)

  def update(caze: Case, fields: Fields)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    update(caze, fields, ModifyConfig.default)

  def update(caze: Case, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    updateSrv(caze, fields, modifyConfig)

  def bulkUpdate(ids: Seq[String], fields: Fields, modifyConfig: ModifyConfig = ModifyConfig.default)(
      implicit authContext: AuthContext,
      ec: ExecutionContext
  ): Future[Seq[Try[Case]]] =
    updateSrv[CaseModel, Case](caseModel, ids, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Case] =
    deleteSrv[CaseModel, Case](caseModel, id)

  def realDelete(id: String)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Unit] =
    get(id).flatMap(realDelete)

  def realDelete(caze: Case)(implicit authContext: AuthContext, ec: ExecutionContext): Future[Unit] = {
    import org.elastic4play.services.QueryDSL._
    for {
      _ <- taskSrv
        .find(withParent(caze), Some("all"), Nil)
        ._1
        .mapAsync(1)(taskSrv.realDelete)
        .runWith(Sink.ignore)
      _ <- artifactSrv
        .find(withParent(caze), Some("all"), Nil)
        ._1
        .mapAsync(1)(artifactSrv.realDelete)
        .runWith(Sink.ignore)
      _ <- auditSrv
        .findFor(caze, Some("all"), Nil)
        ._1
        .mapAsync(1)(auditSrv.realDelete)
        .runWith(Sink.ignore)
      _ = alertSrv
        .find("case" ~= caze.id, Some("all"), Nil)
        ._1
        .mapAsync(1)(alertSrv.unsetCase(_))
      _ <- deleteSrv.realDelete(caze)
    } yield ()
  }

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String])(implicit ec: ExecutionContext): (Source[Case, NotUsed], Future[Long]) =
    findSrv[CaseModel, Case](caseModel, queryDef, range, sortBy)

  def stats(queryDef: QueryDef, aggs: Seq[Agg])(implicit ec: ExecutionContext): Future[JsObject] = findSrv(caseModel, queryDef, aggs: _*)

  def getStats(id: String)(implicit ec: ExecutionContext): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    for {
      taskStats <- taskSrv.stats(
        and(withParent("case", id), "status" in ("Waiting", "InProgress", "Completed")),
        Seq(groupByField("status", selectCount))
      )
      artifactStats <- findSrv(artifactModel, and(withParent("case", id), "status" ~= "Ok"), groupByField("status", selectCount))
    } yield Json.obj(("tasks", taskStats), ("artifacts", artifactStats))
  }

  def linkedCases(id: String)(implicit ec: ExecutionContext): Source[(Case, Seq[Artifact]), NotUsed] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[ArtifactModel, Artifact](
      artifactModel,
      and(parent("case", and(withId(id), "status" ~!= CaseStatus.Deleted, "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)), "status" ~= "Ok"),
      Some("all"),
      Nil
    )._1
      .flatMapConcat { artifact =>
        artifactSrv.findSimilar(artifact, Some("all"), Nil)._1
      }
      .fold(Map.empty[String, List[Artifact]]) { (similarCases, artifact) =>
        val caseId       = artifact.parentId.getOrElse(sys.error(s"Artifact ${artifact.id} has no case !"))
        val artifactList = artifact :: similarCases.getOrElse(caseId, Nil)
        similarCases + (caseId -> artifactList)
      }
      .mapConcat(identity)
      .mapAsyncUnordered(5) {
        case (caseId, artifacts) => getSrv[CaseModel, Case](caseModel, caseId) map (_ -> artifacts)
      }
      .mapMaterializedValue(_ => NotUsed)
  }
}
