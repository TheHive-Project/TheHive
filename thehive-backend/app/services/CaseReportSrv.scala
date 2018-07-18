package services

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import javax.inject.{ Inject, Singleton }
import models.{ CaseReportModel, CaseReport }
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CaseReportSrv @Inject() (
    caseReportModel: CaseReportModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def create(fields: Fields)(implicit authContext: AuthContext): Future[CaseReport] =
    createSrv[CaseReportModel, CaseReport](caseReportModel, fields)

  def get(id: String): Future[CaseReport] =
    getSrv[CaseReportModel, CaseReport](caseReportModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[CaseReport] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[CaseReport] =
    updateSrv[CaseReportModel, CaseReport](caseReportModel, id, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[CaseReportModel, CaseReport](caseReportModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[CaseReport, NotUsed], Future[Long]) = {
    findSrv[CaseReportModel, CaseReport](caseReportModel, queryDef, range, sortBy)
  }
}