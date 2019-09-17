package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._

case class OutputParam(from: Long, to: Long, withStats: Boolean)

@Singleton
class TheHiveQueryExecutor @Inject()(
    caseCtrl: CaseCtrl,
    taskCtrl: TaskCtrl,
//    logCtrl: LogCtrl,
//    observableCtrl: ObservableCtrl,
    alertCtrl: AlertCtrl,
    userCtrl: UserCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
//    dashboardCtrl: DashboardCtrl,
    organisationCtrl: OrganisationCtrl,
    auditCtrl: AuditCtrl,
    implicit val db: Database
) extends QueryExecutor {

  lazy val controllers: List[QueryableCtrl] =
    caseCtrl :: taskCtrl :: alertCtrl :: userCtrl :: caseTemplateCtrl :: organisationCtrl :: auditCtrl :: Nil
  override val version: (Int, Int) = 1 -> 1

  override lazy val publicProperties: List[PublicProperty[_, _]] = controllers.flatMap(_.publicProperties)

  override lazy val queries: Seq[ParamQuery[_]] =
    controllers.map(_.initialQuery) :::
      controllers.map(_.getQuery) :::
      controllers.map(_.pageQuery) :::
      controllers.map(_.outputQuery) :::
      controllers.flatMap(_.extraQueries)
}
