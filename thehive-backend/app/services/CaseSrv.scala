package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.{ Configuration, Logger }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import akka.NotUsed
import akka.stream.scaladsl.Source
import models._

import org.elastic4play.InternalError
import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class CaseSrv(
    maxSimilarCases: Int,
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

  @Inject() def this(
    configuration: Configuration,
    caseModel: CaseModel,
    artifactModel: ArtifactModel,
    taskModel: TaskModel,
    createSrv: CreateSrv,
    artifactSrv: ArtifactSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    ec: ExecutionContext) = this(
    configuration.getOptional[Int]("maxSimilarCases").getOrElse(100),
    caseModel,
    artifactModel,
    taskModel,
    createSrv,
    artifactSrv,
    getSrv,
    updateSrv,
    deleteSrv,
    findSrv,
    ec)

  private[CaseSrv] lazy val logger = Logger(getClass)

  def applyTemplate(template: CaseTemplate, originalFields: Fields): Fields = {
    def getJsObjectOrEmpty(value: Option[JsValue]) = value.fold(JsObject(Nil)) {
      case obj: JsObject ⇒ obj
      case _             ⇒ JsObject(Nil)
    }

    val metricNames = (originalFields.getStrings("metricNames").getOrElse(Nil) ++ template.metricNames()).distinct
    val metrics = JsObject(metricNames.map(_ → JsNull))
    val tags = (originalFields.getStrings("tags").getOrElse(Nil) ++ template.tags()).distinct
    val customFields = getJsObjectOrEmpty(template.customFields()) ++ getJsObjectOrEmpty(originalFields.getValue("customFields"))

    originalFields
      .set("title", originalFields.getString("title").map(t ⇒ JsString(template.titlePrefix().getOrElse("") + " " + t)))
      .set("description", originalFields.getString("description").orElse(template.description()).map(JsString))
      .set("severity", originalFields.getLong("severity").orElse(template.severity()).map(JsNumber(_)))
      .set("tags", JsArray(tags.map(JsString)))
      .set("flag", originalFields.getBoolean("flag").orElse(template.flag()).map(JsBoolean))
      .set("tlp", originalFields.getLong("tlp").orElse(template.tlp()).map(JsNumber(_)))
      .set("metrics", originalFields.getValue("metrics").flatMap(_.asOpt[JsObject]).getOrElse(JsObject(Nil)) ++ metrics)
      .set("customFields", customFields)
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

  def update(caze: Case, fields: Fields)(implicit authContext: AuthContext): Future[Case] =
    updateSrv(caze, fields)

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
      artifactStats ← findSrv(
        artifactModel,
        and("_parent" ~= id, "status" ~= "Ok"),
        groupByField("status", selectCount))
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
      .groupBy(maxSimilarCases, _.parentId)
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
