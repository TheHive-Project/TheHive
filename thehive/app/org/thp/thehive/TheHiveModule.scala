package org.thp.thehive

import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.actor.{ActorRef, Scheduler}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import com.softwaremill.macwire.Module
import org.quartz
import org.quartz.impl.StdSchedulerFactory
import org.thp.scalligraph.auth._
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.janus.ImmenseTermProcessor
import org.thp.scalligraph.models.Schema
import org.thp.scalligraph.services.IntegrityCheck
import org.thp.scalligraph.{ActorSingletonUtils, ErrorHandler, InternalError, LazyMutableSeq, ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.models.{TheHiveSchemaDefinition, UseHashToIndex}
import org.thp.thehive.services.notification.notifiers._
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.notification.{NotificationActor, NotificationSrv, NotificationTag}
import org.thp.thehive.services.{UserSrv => TheHiveUserSrv, _}
import play.api.routing.SimpleRouter
import play.api.routing.sird._
import play.api.{Logger, Mode}

@Module
class TheHiveModule(app: ScalligraphApplication) extends ScalligraphModule with ActorSingletonUtils {
  import app.actorSystem
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  lazy val logger: Logger                                 = Logger(getClass)
  lazy val notificationActor: ActorRef @@ NotificationTag = wireActor[NotificationActor]("notification").taggedWith[NotificationTag]
  lazy val flowActor: ActorRef @@ FlowTag                 = wireActorSingleton(actorSystem, wireProps[FlowActor], "flow-actor").taggedWith[FlowTag]
  lazy val integrityCheckActor: TypedActorRef[IntegrityCheck.Request] = ClusterSingleton(app.actorSystem.toTyped)
    .init(SingletonActor(IntegrityCheck.behavior(app.database, quartzScheduler, app.applicationConfig, integrityChecks()), "IntegrityCheckActor"))
  lazy val caseNumberActor: TypedActorRef[CaseNumberActor.Request] = {
    val behavior = CaseNumberActor.behavior(app.database, app.applicationConfig, caseSrv)
    if (app.application.mode == Mode.Test)
      actorSystem.toTyped.systemActorOf(behavior, "CaseNumberLeader")
    else
      ClusterSingleton(app.actorSystem.toTyped)
        .init(SingletonActor(behavior, "CaseNumberLeader"))
  }

  lazy val scheduler: Scheduler = app.actorSystem.scheduler

  lazy val auditSrv: AuditSrv                       = wire[AuditSrv]
  lazy val roleSrv: RoleSrv                         = wire[RoleSrv]
  lazy val organisationSrv: OrganisationSrv         = wire[OrganisationSrv]
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
  lazy val customFieldValueSrv: CustomFieldValueSrv = wire[CustomFieldValueSrv]
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
  lazy val searchSrv: SearchSrv                     = wire[SearchSrv]

  lazy val connectors: LazyMutableSeq[Connector] = LazyMutableSeq[Connector]

  lazy val quartzScheduler: quartz.Scheduler = {
    val factory   = new StdSchedulerFactory
    val scheduler = factory.getScheduler()
    actorSystem.registerOnTermination(scheduler.shutdown())
    scheduler
  }

//  lazy val authSrv: AuthSrv = wire[TOTPAuthSrv]

  app.authSrvProviders += wire[ADAuthProvider]
  app.authSrvProviders += wire[LdapAuthProvider]
  app.authSrvProviders += wire[LocalPasswordAuthProvider]
  app.authSrvProviders += wire[LocalKeyAuthProvider]
  app.authSrvProviders += wire[BasicAuthProvider]
  app.authSrvProviders += wire[HeaderAuthProvider]
  app.authSrvProviders += wire[PkiAuthProvider]
  app.authSrvProviders += wire[SessionAuthProvider]
  app.authSrvProviders += wire[OAuth2Provider]

  lazy val triggerProviders: LazyMutableSeq[TriggerProvider] = LazyMutableSeq[TriggerProvider](
    () => wire[AlertCreatedProvider],
    () => wire[AnyEventProvider],
    () => wire[CaseCreatedProvider],
    () => wire[FilteredEventProvider],
    () => wire[JobFinishedProvider],
    () => wire[LogInMyTaskProvider],
    () => wire[TaskAssignedProvider],
    () => wire[CaseShareProvider]
  )

  lazy val schema: Schema = app.schemas.reduceOption[Schema](_ + _).getOrElse(throw InternalError("No schema defined"))
  lazy val notifierProviders: LazyMutableSeq[NotifierProvider] = LazyMutableSeq(
    () => wire[AppendToFileProvider],
    () => wire[EmailerProvider],
    () => wire[MattermostProvider],
    () => wire[WebhookProvider]
  )
  lazy val integrityChecks: LazyMutableSeq[IntegrityCheck] = LazyMutableSeq(
    () => wire[ProfileIntegrityCheck],
    () => wire[OrganisationIntegrityCheck],
    () => wire[TagIntegrityCheck],
    () => wire[UserIntegrityCheck],
    () => wire[ImpactStatusIntegrityCheck],
    () => wire[ResolutionStatusIntegrityCheck],
    () => wire[ObservableTypeIntegrityCheck],
    () => wire[CustomFieldIntegrityCheck],
    () => wire[CaseTemplateIntegrityCheck],
    () => wire[DataIntegrityCheck],
    () => wire[CaseIntegrityCheck],
    () => wire[AlertIntegrityCheck],
    () => wire[TaskIntegrityCheck],
    () => wire[ObservableIntegrityCheck],
    () => wire[LogIntegrityCheck],
    () => wire[RoleIntegrityCheck]
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
  ImmenseTermProcessor.registerStrategy("observableHashToIndex", _ => UseHashToIndex)

  val entityDescriptions: LazyMutableSeq[(Int, ModelDescription)] = LazyMutableSeq[(Int, ModelDescription)]

  override def init(): Unit = {
    integrityCheckActor
    notificationActor
    app.router
    quartzScheduler.start()
    ()
  }
}
