package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import models.{ CaseReportingTemplate, CaseReportingTemplateModel }

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

@Singleton
class CaseReportingTemplateSrv @Inject() (
    caseReportingTemplateModel: CaseReportingTemplateModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def create(fields: Fields)(implicit authContext: AuthContext): Future[CaseReportingTemplate] =
    createSrv[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, fields)

  def get(id: String): Future[CaseReportingTemplate] =
    getSrv[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, id)

  def getByName(name: String): Future[CaseReportingTemplate] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, "name" ~= name, Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Case reporting template $name not found")))
  }

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[CaseReportingTemplate] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[CaseReportingTemplate] =
    updateSrv[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, id, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[CaseReportingTemplate, NotUsed], Future[Long]) = {
    findSrv[CaseReportingTemplateModel, CaseReportingTemplate](caseReportingTemplateModel, queryDef, range, sortBy)
  }
}
