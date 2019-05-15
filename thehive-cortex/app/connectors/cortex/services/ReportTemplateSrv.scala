package connectors.cortex.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.Logger
import play.api.libs.json.JsObject

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{Agg, AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv, QueryDef, UpdateSrv}
import connectors.cortex.models.{ReportTemplate, ReportTemplateModel}
import services.ArtifactSrv

import org.elastic4play.database.ModifyConfig

@Singleton
class ReportTemplateSrv @Inject()(
    reportTemplateModel: ReportTemplateModel,
    createSrv: CreateSrv,
    artifactSrv: ArtifactSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext
) {

  private[ReportTemplateSrv] lazy val logger = Logger(getClass)

  def create(fields: Fields)(implicit authContext: AuthContext): Future[ReportTemplate] =
    createSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, fields)

  def get(id: String): Future[ReportTemplate] =
    getSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[ReportTemplate] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[ReportTemplate] =
    updateSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, id, fields, modifyConfig)

  def bulkUpdate(ids: Seq[String], fields: Fields)(implicit authContext: AuthContext): Future[Seq[Try[ReportTemplate]]] =
    bulkUpdate(ids, fields, ModifyConfig.default)

  def bulkUpdate(ids: Seq[String], fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[Seq[Try[ReportTemplate]]] =
    updateSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, ids, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[ReportTemplateModel, ReportTemplate](reportTemplateModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[ReportTemplate, NotUsed], Future[Long]) =
    findSrv[ReportTemplateModel, ReportTemplate](reportTemplateModel, queryDef, range, sortBy)

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(reportTemplateModel, queryDef, aggs: _*)

  def getStats(id: String): Future[JsObject] =
    Future.successful(JsObject.empty)
}
