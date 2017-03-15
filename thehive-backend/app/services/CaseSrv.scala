package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

import akka.NotUsed
import akka.stream.scaladsl.Source

import play.api.Logger
import play.api.libs.json.{ JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import org.elastic4play.InternalError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Agg, AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv, QueryDef, UpdateSrv }

import models.{ Artifact, ArtifactModel, Case, CaseModel, CaseResolutionStatus, CaseStatus, CaseTemplate, Task, TaskModel }

@Singleton
class CaseSrv @Inject() (
    caseModel: CaseModel,
    artifactModel: ArtifactModel,
    taskModel: TaskModel,
    createSrv: CreateSrv,
    artifactSrv: ArtifactSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  lazy val log = Logger(getClass)

  def applyTemplate(template: CaseTemplate, originalFields: Fields): Fields = {
    val metricNames = (originalFields.getStrings("metricNames").getOrElse(Nil) ++ template.metricNames()).distinct
    val metrics = JsObject(metricNames.map(_ → JsNull))
    val tags = (originalFields.getStrings("tags").getOrElse(Nil) ++ template.tags()).distinct
    originalFields
      .set("title", originalFields.getString("title").map(title ⇒ JsString(template.titlePrefix().getOrElse("") + " " + title)))
      .set("description", originalFields.getString("description").orElse(template.description()).map(JsString(_)))
      .set("severity", originalFields.getLong("severity").orElse(template.severity()).map(JsNumber(_)))
      .set("tags", JsArray(tags.map(JsString(_))))
      .set("flag", originalFields.getBoolean("flag").orElse(template.flag()).map(JsBoolean(_)))
      .set("tlp", originalFields.getLong("tlp").orElse(template.tlp()).map(JsNumber(_)))
      .set("metrics", originalFields.getValue("metrics").flatMap(_.asOpt[JsObject]).getOrElse(JsObject(Nil)) ++ metrics)
  }

  def create(fields: Fields, template: Option[CaseTemplate] = None)(implicit authContext: AuthContext): Future[Case] = {
    val fieldsWithOwner = fields.get("owner") match {
      case None    ⇒ fields.set("owner", authContext.userId)
      case Some(_) ⇒ fields
    }
    val templatedCaseFields = template match {
      case None    ⇒ fieldsWithOwner
      case Some(t) ⇒ applyTemplate(t, fieldsWithOwner)
    }
    createSrv[CaseModel, Case](caseModel, templatedCaseFields.unset("tasks"))
      .flatMap { caze ⇒
        val taskFields = fields.getValues("tasks").collect {
          case task: JsObject ⇒ Fields(task)
        } ++ template.map(_.tasks().map(Fields(_))).getOrElse(Nil)
        createSrv[TaskModel, Task, Case](taskModel, taskFields.map(caze → _))
          .map(_ ⇒ caze)
      }
  }

  def get(id: String): Future[Case] =
    getSrv[CaseModel, Case](caseModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Case] =
    updateSrv[CaseModel, Case](caseModel, id, fields)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[Case]]] = {
    updateSrv[CaseModel, Case](caseModel, ids, fields)
  }

  def delete(id: String)(implicit Context: AuthContext): Future[Case] =
    deleteSrv[CaseModel, Case](caseModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Case, NotUsed], Future[Long]) = {
    findSrv[CaseModel, Case](caseModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(caseModel, queryDef, aggs: _*)

  def getStats(id: String): Future[JsObject] = {
    import org.elastic4play.services.QueryDSL._
    for {
      taskStats ← findSrv(taskModel, and(
        "_parent" ~= id,
        "status" in ("Waiting", "InProgress", "Completed")), groupByField("status", selectCount))
      artifactStats ← findSrv(artifactModel, and("_parent" ~= id, "status" ~= "Ok"), groupByField("status", selectCount))
    } yield Json.obj(("tasks", taskStats), ("artifacts", artifactStats))
  }

  def linkedCases(id: String): Source[(Case, Seq[Artifact]), NotUsed] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[ArtifactModel, Artifact](
      artifactModel,
      and(
        parent("case", and(
          withId(id),
          "status" ~!= CaseStatus.Deleted,
          "resolutionStatus" ~!= CaseResolutionStatus.Duplicated)),
        "status" ~= "Ok"), Some("all"), Nil)
      ._1
      .flatMapConcat { artifact ⇒ artifactSrv.findSimilar(artifact, Some("all"), Nil)._1 }
      .groupBy(20, _.parentId)
      .map { a ⇒ (a.parentId, Seq(a)) }
      .reduce((l, r) ⇒ (l._1, r._2 ++ l._2))
      .mergeSubstreams
      .mapAsyncUnordered(5) {
        case (Some(caseId), artifacts) ⇒ getSrv[CaseModel, Case](caseModel, caseId) map (_ → artifacts)
        case _                         ⇒ Future.failed(InternalError("Case not found"))
      }
      .mapMaterializedValue(_ ⇒ NotUsed)
  }
}
