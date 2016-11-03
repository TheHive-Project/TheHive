package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AuthContext, CreateSrv, DeleteSrv, FindSrv, GetSrv, QueryDSL, QueryDef, UpdateSrv }

import models.{ CaseTemplate, CaseTemplateModel }

@Singleton
class CaseTemplateSrv @Inject() (
    caseTemplateModel: CaseTemplateModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  def create(fields: Fields)(implicit authContext: AuthContext) =
    createSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, fields)

  def get(id: String)(implicit Context: AuthContext) =
    getSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, id)

  def getByName(name: String): Future[CaseTemplate] = {
    import QueryDSL._
    findSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, "name" ~= name, Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Case template $name not found")))
  }

  def update(id: String, fields: Fields)(implicit Context: AuthContext) =
    updateSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, id, fields)

  def delete(id: String)(implicit Context: AuthContext) =
    deleteSrv.realDelete[CaseTemplateModel, CaseTemplate](caseTemplateModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[CaseTemplate, NotUsed], Future[Long]) = {
    findSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, queryDef, range, sortBy)
  }
}