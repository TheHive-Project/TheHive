package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import models.{CaseTemplate, CaseTemplateModel}

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Fields
import org.elastic4play.database.ModifyConfig
import org.elastic4play.services._

@Singleton
class CaseTemplateSrv @Inject()(
    caseTemplateModel: CaseTemplateModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) {

  def create(fields: Fields)(implicit authContext: AuthContext): Future[CaseTemplate] =
    createSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, fields)

  def get(id: String): Future[CaseTemplate] =
    getSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, id)

  def getByName(name: String): Future[CaseTemplate] = {
    import org.elastic4play.services.QueryDSL._
    findSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, "name" ~= name, Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
      .map(_.getOrElse(throw NotFoundError(s"Case template $name not found")))
  }

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[CaseTemplate] =
    update(id, fields, ModifyConfig.default)

  def update(id: String, fields: Fields, modifyConfig: ModifyConfig)(implicit authContext: AuthContext): Future[CaseTemplate] =
    updateSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, id, fields, modifyConfig)

  def delete(id: String)(implicit authContext: AuthContext): Future[Unit] =
    deleteSrv.realDelete[CaseTemplateModel, CaseTemplate](caseTemplateModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[CaseTemplate, NotUsed], Future[Long]) =
    findSrv[CaseTemplateModel, CaseTemplate](caseTemplateModel, queryDef, range, sortBy)
}
