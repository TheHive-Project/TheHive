package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthSrvProvider
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.{ErrorHandler, ScalligraphModule}
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services._
import play.api.http.HttpErrorHandler
import play.api.routing.{Router => PlayRouter}

object TheHiveModuleV1 extends ScalligraphModule {
  import com.softwaremill.macwire._
  import org.thp.thehive.TheHiveModule._
  import scalligraphApplication._

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
  lazy val theHiveModelDescription: TheHiveModelDescription = wire[TheHiveModelDescription]
  lazy val entityDescriptions: Seq[ModelDescription] = connectors
    .toSeq
    .flatMap(_.modelDescriptions.get(1)) :+ theHiveModelDescription

  lazy val properties: Properties     = wire[Properties]
  lazy val shareCtrl: ShareCtrl       = wire[ShareCtrl]
  lazy val statusCtrl: StatusCtrl     = wire[StatusCtrl]
  lazy val describeCtrl: DescribeCtrl = wire[DescribeCtrl]

  lazy val userConfigContext: UserConfigContext                 = wire[UserConfigContext]
  lazy val organisationConfigContext: OrganisationConfigContext = wire[OrganisationConfigContext]

  lazy val queryExecutor: TheHiveQueryExecutor = wire[TheHiveQueryExecutor]

  lazy val errorHandler: HttpErrorHandler = ErrorHandler

  override lazy val routers: Set[PlayRouter]           = Set(wire[Router].withPrefix("/api/v1"))
  override lazy val queryExecutors: Set[QueryExecutor] = Set(queryExecutor)
  override val schemas: Set[UpdatableSchema]           = Set.empty
  override val authSrvProviders: Set[AuthSrvProvider]  = Set.empty
}
