package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.libs.json._

import akka.NotUsed
import akka.stream.scaladsl.Source
import models._

import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class DashboardSrv @Inject() (
    dashboardModel: DashboardModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    implicit val ec: ExecutionContext) {

  private[DashboardSrv] lazy val logger = Logger(getClass)

  def create(fields: Fields)(implicit authContext: AuthContext): Future[Dashboard] = {
    createSrv[DashboardModel, Dashboard](dashboardModel, fields)
  }

  def get(id: String): Future[Dashboard] =
    getSrv[DashboardModel, Dashboard](dashboardModel, id)

  def update(id: String, fields: Fields)(implicit authContext: AuthContext): Future[Dashboard] =
    updateSrv[DashboardModel, Dashboard](dashboardModel, id, fields)

  def update(caze: Dashboard, fields: Fields)(implicit authContext: AuthContext): Future[Dashboard] =
    updateSrv(caze, fields)

  def delete(id: String)(implicit Context: AuthContext): Future[Dashboard] =
    deleteSrv[DashboardModel, Dashboard](dashboardModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[Dashboard, NotUsed], Future[Long]) = {
    findSrv[DashboardModel, Dashboard](dashboardModel, queryDef, range, sortBy)
  }

  def stats(queryDef: QueryDef, aggs: Seq[Agg]): Future[JsObject] = findSrv(dashboardModel, queryDef, aggs: _*)
}
