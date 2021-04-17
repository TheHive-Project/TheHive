package org.thp.thehive.controllers.v0

import org.thp.scalligraph.auth.AuthSrvProvider
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.{ErrorHandler, ScalligraphModule}
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services.{OrganisationConfigContext, UserConfigContext}
import play.api.http.HttpErrorHandler
import play.api.routing.{Router => PlayRouter}

object TheHiveModuleV0 extends ScalligraphModule {
  import com.softwaremill.macwire._
  import org.thp.thehive.TheHiveModule._
  import scalligraphApplication._

  lazy val authenticationCtrl: AuthenticationCtrl = wire[AuthenticationCtrl]

  lazy val alertCtrl: AlertCtrl                       = wire[AlertCtrl]
  lazy val publicAlert: PublicAlert                   = wire[PublicAlert]
  lazy val auditCtrl: AuditCtrl                       = wire[AuditCtrl]
  lazy val publicAudit: PublicAudit                   = wire[PublicAudit]
  lazy val caseCtrl: CaseCtrl                         = wire[CaseCtrl]
  lazy val publicCase: PublicCase                     = wire[PublicCase]
  lazy val caseTemplateCtrl: CaseTemplateCtrl         = wire[CaseTemplateCtrl]
  lazy val publicCaseTemplate: PublicCaseTemplate     = wire[PublicCaseTemplate]
  lazy val customFieldCtrl: CustomFieldCtrl           = wire[CustomFieldCtrl]
  lazy val publicCustomField: PublicCustomField       = wire[PublicCustomField]
  lazy val observableTypeCtrl: ObservableTypeCtrl     = wire[ObservableTypeCtrl]
  lazy val publicObservableType: PublicObservableType = wire[PublicObservableType]
  lazy val dashboardCtrl: DashboardCtrl               = wire[DashboardCtrl]
  lazy val publicDashboard: PublicDashboard           = wire[PublicDashboard]
  lazy val logCtrl: LogCtrl                           = wire[LogCtrl]
  lazy val publicLog: PublicLog                       = wire[PublicLog]
  lazy val observableCtrl: ObservableCtrl             = wire[ObservableCtrl]
  lazy val publicObservable: PublicObservable         = wire[PublicObservable]
  lazy val organisationCtrl: OrganisationCtrl         = wire[OrganisationCtrl]
  lazy val publicOrganisation: PublicOrganisation     = wire[PublicOrganisation]
  lazy val profileCtrl: ProfileCtrl                   = wire[ProfileCtrl]
  lazy val publicProfile: PublicProfile               = wire[PublicProfile]
  lazy val pageCtrl: PageCtrl                         = wire[PageCtrl]
  lazy val publicPage: PublicPage                     = wire[PublicPage]
  lazy val taskCtrl: TaskCtrl                         = wire[TaskCtrl]
  lazy val publicTask: PublicTask                     = wire[PublicTask]
  lazy val tagCtrl: TagCtrl                           = wire[TagCtrl]
  lazy val publicTag: PublicTag                       = wire[PublicTag]
  lazy val userCtrl: UserCtrl                         = wire[UserCtrl]
  lazy val publicUser: PublicUser                     = wire[PublicUser]

  lazy val streamCtrl: StreamCtrl                           = wire[StreamCtrl]
  lazy val shareCtrl: ShareCtrl                             = wire[ShareCtrl]
  lazy val statusCtrl: StatusCtrl                           = wire[StatusCtrl]
  lazy val statsCtrl: StatsCtrl                             = wire[StatsCtrl]
  lazy val theHiveModelDescription: TheHiveModelDescription = wire[TheHiveModelDescription]
  lazy val entityDescriptions: Seq[ModelDescription] = connectors
    .toSeq
    .flatMap(_.modelDescriptions.get(0)) :+ theHiveModelDescription

  lazy val permissionCtrl: PermissionCtrl = wire[PermissionCtrl]
  lazy val describeCtrl: DescribeCtrl     = wire[DescribeCtrl]
  lazy val listCtrl: ListCtrl             = wire[ListCtrl]

  lazy val configCtrl: ConfigCtrl                               = wire[ConfigCtrl]
  lazy val userConfigContext: UserConfigContext                 = wire[UserConfigContext]
  lazy val organisationConfigContext: OrganisationConfigContext = wire[OrganisationConfigContext]

  lazy val attachmentCtrl: AttachmentCtrl      = wire[AttachmentCtrl]
  lazy val queryExecutor: TheHiveQueryExecutor = wire[TheHiveQueryExecutor]

  lazy val errorHandler: HttpErrorHandler = ErrorHandler

  lazy val router: Router                              = wire[Router]
  override lazy val routers: Set[PlayRouter]           = Set(router.withPrefix("/api"), router.withPrefix("/api/v0"))
  override lazy val queryExecutors: Set[QueryExecutor] = Set(queryExecutor)
  override val schemas: Set[UpdatableSchema]           = Set.empty
  override val authSrvProviders: Set[AuthSrvProvider]  = Set.empty
}
