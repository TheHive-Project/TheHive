package org.thp.thehive.controllers.v1

import org.thp.scalligraph.{ErrorHandler, ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.services._
import play.api.http.HttpErrorHandler

class TheHiveModuleV1(app: ScalligraphApplication) extends ScalligraphModule {

  import com.softwaremill.macwire._

  lazy val theHiveModule: TheHiveModule           = app.getModule[TheHiveModule]
  lazy val authenticationCtrl: AuthenticationCtrl = wire[AuthenticationCtrl]

  lazy val alertCtrl: AlertCtrl                             = wire[AlertCtrl]
  lazy val auditCtrl: AuditCtrl                             = wire[AuditCtrl]
  lazy val caseCtrl: CaseCtrl                               = wire[CaseCtrl]
  lazy val caseTemplateCtrl: CaseTemplateCtrl               = wire[CaseTemplateCtrl]
  lazy val customFieldCtrl: CustomFieldCtrl                 = wire[CustomFieldCtrl]
  lazy val observableTypeCtrl: ObservableTypeCtrl           = wire[ObservableTypeCtrl]
  lazy val dashboardCtrl: DashboardCtrl                     = wire[DashboardCtrl]
  lazy val logCtrl: LogCtrl                                 = wire[LogCtrl]
  lazy val observableCtrl: ObservableCtrl                   = wire[ObservableCtrl]
  lazy val organisationCtrl: OrganisationCtrl               = wire[OrganisationCtrl]
  lazy val profileCtrl: ProfileCtrl                         = wire[ProfileCtrl]
  lazy val taskCtrl: TaskCtrl                               = wire[TaskCtrl]
  lazy val tagCtrl: TagCtrl                                 = wire[TagCtrl]
  lazy val userCtrl: UserCtrl                               = wire[UserCtrl]
  lazy val patternCtrl: PatternCtrl                         = wire[PatternCtrl]
  lazy val procedureCtrl: ProcedureCtrl                     = wire[ProcedureCtrl]
  lazy val adminCtrl: AdminCtrl                             = wire[AdminCtrl]
  lazy val taxonomyCtrl: TaxonomyCtrl                       = wire[TaxonomyCtrl]
  lazy val monitoringCtrl: MonitoringCtrl                   = wire[MonitoringCtrl]
  lazy val searchCtrl: SearchCtrl                           = wire[SearchCtrl]
  lazy val theHiveModelDescription: TheHiveModelDescription = wire[TheHiveModelDescription]
  theHiveModule.entityDescriptions += (1 -> theHiveModelDescription)

  lazy val properties: Properties     = wire[Properties]
  lazy val shareCtrl: ShareCtrl       = wire[ShareCtrl]
  lazy val statusCtrl: StatusCtrl     = wire[StatusCtrl]
  lazy val describeCtrl: DescribeCtrl = wire[DescribeCtrl]

  lazy val userConfigContext: UserConfigContext                 = wire[UserConfigContext]
  lazy val organisationConfigContext: OrganisationConfigContext = wire[OrganisationConfigContext]

  lazy val queryExecutor: TheHiveQueryExecutor = wire[TheHiveQueryExecutor]

  lazy val errorHandler: HttpErrorHandler = ErrorHandler

  app.routers += wire[Router].withPrefix("/api/v1")
  app.queryExecutors += queryExecutor
}
