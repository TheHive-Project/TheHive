package org.thp.thehive

import akka.actor.ActorRef
import com.softwaremill.macwire.Module
import org.thp.scalligraph.auth._
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Schema
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigActor, ConfigTag}
import org.thp.scalligraph.services.{EventSrv, GenIntegrityCheckOps}
import org.thp.scalligraph.{ActorSingletonUtils, ErrorHandler, ScalligraphApplication, ScalligraphModule, SemiMutableSeq}
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.models.TheHiveSchemaDefinition
import org.thp.thehive.services.notification.notifiers._
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.notification.{NotificationActor, NotificationSrv, NotificationTag}
import org.thp.thehive.services.{UserSrv => TheHiveUserSrv, _}
import play.api.Logger
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Module
class TheHiveModule(app: ScalligraphApplication) extends ScalligraphModule with ActorSingletonUtils {
  import app.actorSystem
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  lazy val logger: Logger                                 = Logger(getClass)
  lazy val notificationActor: ActorRef @@ NotificationTag = wireActor[NotificationActor]("notification").taggedWith[NotificationTag]
  lazy val configActor: ActorRef @@ ConfigTag             = wireActorSingleton(actorSystem, wireProps[ConfigActor], "config-actor").taggedWith[ConfigTag]
  lazy val flowActor: ActorRef @@ FlowTag                 = wireActorSingleton(actorSystem, wireProps[FlowActor], "flow-actor").taggedWith[FlowTag]
  lazy val integrityCheckActor: ActorRef @@ IntegrityCheckTag =
    wireActorSingleton(actorSystem, wireProps[IntegrityCheckActor], "integrity-check-actor").taggedWith[IntegrityCheckTag]

  lazy val eventSrv: EventSrv                       = wire[EventSrv]
  lazy val auditSrv: AuditSrv                       = wire[AuditSrv]
  lazy val roleSrv: RoleSrv                         = wire[RoleSrv]
  lazy val organisationSrv: OrganisationSrv         = wire[OrganisationSrv]
  lazy val applicationConfig: ApplicationConfig     = wire[ApplicationConfig]
  lazy val userSrv: TheHiveUserSrv                  = wire[TheHiveUserSrv]
  lazy val localUsrSrv: LocalUserSrv                = wire[LocalUserSrv]
  lazy val taxonomySrv: TaxonomySrv                 = wire[TaxonomySrv]
  lazy val tagSrv: TagSrv                           = wire[TagSrv]
  lazy val configSrv: ConfigSrv                     = wire[ConfigSrv]
  lazy val attachmentSrv: AttachmentSrv             = wire[AttachmentSrv]
  lazy val profileSrv: ProfileSrv                   = wire[ProfileSrv]
  lazy val NotificationSrv: NotificationSrv         = wire[NotificationSrv]
  lazy val logSrv: LogSrv                           = wire[LogSrv]
  lazy val taskSrv: TaskSrv                         = wire[TaskSrv]
  lazy val shareSrv: ShareSrv                       = wire[ShareSrv]
  lazy val customFieldSrv: CustomFieldSrv           = wire[CustomFieldSrv]
  lazy val caseSrv: CaseSrv                         = wire[CaseSrv]
  lazy val impactStatusSrv: ImpactStatusSrv         = wire[ImpactStatusSrv]
  lazy val resolutionStatusSrv: ResolutionStatusSrv = wire[ResolutionStatusSrv]
  lazy val observableSrv: ObservableSrv             = wire[ObservableSrv]
  lazy val requestOrganisation: RequestOrganisation = wire[RequestOrganisation]
  lazy val dataSrv: DataSrv                         = wire[DataSrv]
  lazy val alertSrv: AlertSrv                       = wire[AlertSrv]
  lazy val observableTypeSrv: ObservableTypeSrv     = wire[ObservableTypeSrv]
  lazy val caseTemplateSrv: CaseTemplateSrv         = wire[CaseTemplateSrv]
  lazy val dashboardSrv: DashboardSrv               = wire[DashboardSrv]
  lazy val pageSrv: PageSrv                         = wire[PageSrv]
  lazy val streamSrv: StreamSrv                     = wire[StreamSrv]
  lazy val patternSrv: PatternSrv                   = wire[PatternSrv]
  lazy val procedureSrv: ProcedureSrv               = wire[ProcedureSrv]

  lazy val connectors: SemiMutableSeq[Connector] = SemiMutableSeq[Connector]

  lazy val authSrv: AuthSrv = wire[TOTPAuthSrv]

  app.authSrvProviders += wire[ADAuthProvider]
  app.authSrvProviders += wire[LdapAuthProvider]
  app.authSrvProviders += wire[LocalPasswordAuthProvider]
  app.authSrvProviders += wire[LocalKeyAuthProvider]
  app.authSrvProviders += wire[BasicAuthProvider]
  app.authSrvProviders += wire[HeaderAuthProvider]
  app.authSrvProviders += wire[PkiAuthProvider]
  app.authSrvProviders += wire[SessionAuthProvider]
  app.authSrvProviders += wire[OAuth2Provider]

  lazy val triggerProviders: SemiMutableSeq[TriggerProvider] = SemiMutableSeq[TriggerProvider](
    () => wire[AlertCreatedProvider],
    () => wire[AnyEventProvider],
    () => wire[CaseCreatedProvider],
    () => wire[FilteredEventProvider],
    () => wire[JobFinishedProvider],
    () => wire[LogInMyTaskProvider],
    () => wire[TaskAssignedProvider],
    () => wire[CaseShareProvider]
  )

  lazy val schema: Schema = app.schemas.reduceOption[Schema](_ + _).getOrElse(???)
  lazy val notifierProviders: SemiMutableSeq[NotifierProvider] = SemiMutableSeq(
    () => wire[AppendToFileProvider],
    () => wire[EmailerProvider],
    () => wire[MattermostProvider],
    () => wire[WebhookProvider]
  )
  lazy val integrityChecks: SemiMutableSeq[GenIntegrityCheckOps] = SemiMutableSeq(
    () => wire[ProfileIntegrityCheckOps],
    () => wire[OrganisationIntegrityCheckOps],
    () => wire[TagIntegrityCheckOps],
    () => wire[UserIntegrityCheckOps],
    () => wire[ImpactStatusIntegrityCheckOps],
    () => wire[ResolutionStatusIntegrityCheckOps],
    () => wire[ObservableTypeIntegrityCheckOps],
    () => wire[CustomFieldIntegrityCheckOps],
    () => wire[CaseTemplateIntegrityCheckOps],
    () => wire[DataIntegrityCheckOps],
    () => wire[CaseIntegrityCheckOps],
    () => wire[AlertIntegrityCheckOps]
  )

  lazy val entrypoint: Entrypoint     = wire[Entrypoint]
  val errorHandler: ErrorHandler.type = ErrorHandler

  app.routers += SimpleRouter {
    case POST(p"/api/v${int(version)}/query") =>
      val queryExecutor = app.getQueryExecutor(version)
      entrypoint("query")
        .extract("query", queryExecutor.parser.on("query"))
        .auth { request =>
          queryExecutor.execute(request.body("query"), request)
        }
  }

  app.schemas += TheHiveSchemaDefinition

  val entityDescriptions: SemiMutableSeq[(Int, ModelDescription)] = SemiMutableSeq[(Int, ModelDescription)]
}
