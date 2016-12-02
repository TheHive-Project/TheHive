package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import akka.NotUsed
import akka.stream.scaladsl.Source

import play.api.Logger
import play.api.libs.json.JsObject

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ Agg, AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv, QueryDef, UpdateSrv }

import models.{ ReportTemplate, ReportTemplateModel }

@Singleton
class ReportTemplateSrv @Inject() (
    reportTemplateModel: ReportTemplateModel,
    createSrv: CreateSrv,
    artifactSrv: ArtifactSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  lazy val log = Logger(getClass)

  def create(fields: Fields)(implicit authContext: AuthContext): Future[ReportTemplate] = {
    createSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, fields)
  }

  def get(id: String): Future[ReportTemplate] =
    getSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[ReportTemplate] =
    updateSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, id, fields)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[ReportTemplate]]] = {
    updateSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, ids, fields)
  }

  def delete(id: String)(implicit Context: AuthContext): Future[ReportTemplate] =
    deleteSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[ReportTemplate, NotUsed], Future[Long]) = {
    findSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(reportTemplateModel, queryDef, aggs: _*)

  def getStats(id: String): Future[JsObject] = {
    Future.successful(JsObject(Nil))
  }
}