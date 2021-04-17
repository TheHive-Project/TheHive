package org.thp.thehive

import akka.actor.ActorRef
import org.thp.scalligraph.auth._
import org.thp.scalligraph.controllers.{AuthenticatedRequest, Entrypoint}
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigActor, ConfigTag}
import org.thp.scalligraph.services.{EventSrv, GenIntegrityCheckOps}
import org.thp.scalligraph.{ErrorHandler, NotFoundError, ScalligraphModule}
import org.thp.thehive.models.TheHiveSchemaDefinition
import org.thp.thehive.services.notification.notifiers._
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.notification.{NotificationActor, NotificationSrv, NotificationTag}
import org.thp.thehive.services.{UserSrv => TheHiveUserSrv, _}
import play.api.Logger
import play.api.mvc.{ActionFunction, Request, Result}
import play.api.routing.sird._
import play.api.routing.{Router, SimpleRouter}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.{universe => ru}

object TheHiveModule extends ScalligraphModule { module =>
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._
  import scalligraphApplication._

  lazy val logger: Logger = Logger(getClass)

  lazy val notificationActor: ActorRef @@ NotificationTag = wireActor[NotificationActor]("notification").taggedWith[NotificationTag]
  lazy val configActor: ActorRef @@ ConfigTag             = wireActorSingleton(wireProps[ConfigActor], "config-actor").taggedWith[ConfigTag]
  lazy val flowActor: ActorRef @@ FlowTag                 = wireActorSingleton(wireProps[FlowActor], "flow-actor").taggedWith[FlowTag]
  lazy val integrityCheckActor: ActorRef @@ IntegrityCheckTag =
    wireActorSingleton(wireProps[IntegrityCheckActor], "integrity-check-actor").taggedWith[IntegrityCheckTag]

  lazy val eventSrv: EventSrv                       = wire[EventSrv]
  lazy val auditSrv: AuditSrv                       = wire[AuditSrv]
  lazy val roleSrv: RoleSrv                         = wire[RoleSrv]
  lazy val organisationSrv: OrganisationSrv         = wire[OrganisationSrv]
  lazy val applicationConfig: ApplicationConfig     = wire[ApplicationConfig]
  lazy val thehiveUserSrv: TheHiveUserSrv           = wire[TheHiveUserSrv]
  lazy val userSrv: LocalUserSrv                    = wire[LocalUserSrv]
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

  lazy val connectors: Set[Connector] = {
    val rm: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
    configuration
      .get[Seq[String]]("thehive.connectors")
      .flatMap { moduleName =>
        rm.reflectModule(rm.staticModule(moduleName)).instance match {
          case obj: Connector =>
            logger.info(s"Loading connector ${obj.getClass.getSimpleName.stripSuffix("$")}")
            obj.configure(scalligraphApplication)
            Some(obj)
          case obj =>
            logger.error(s"Fail to load connector ${obj.getClass.getSimpleName}")
            None
        }
      }
  }.toSet

  lazy val authSrv: AuthSrv = wire[TOTPAuthSrv]
  override lazy val authSrvProviders: Set[AuthSrvProvider] = Set(
    wire[ADAuthProvider],
    wire[LdapAuthProvider],
    wire[LocalPasswordAuthProvider],
    wire[LocalKeyAuthProvider],
    wire[BasicAuthProvider],
    wire[HeaderAuthProvider],
    wire[PkiAuthProvider],
    wire[SessionAuthProvider],
    wire[OAuth2Provider]
  )

  lazy val triggerProviders: Set[TriggerProvider] = Set(
    wire[AlertCreatedProvider],
    wire[AnyEventProvider],
    wire[CaseCreatedProvider],
    wire[FilteredEventProvider],
    wire[JobFinishedProvider],
    wire[LogInMyTaskProvider],
    wire[TaskAssignedProvider],
    wire[CaseShareProvider]
  )

  lazy val schema: UpdatableSchema = TheHiveSchemaDefinition
  lazy val notifierProviders: Set[NotifierProvider] = Set(
    wire[AppendToFileProvider],
    wire[EmailerProvider],
    wire[MattermostProvider],
    wire[WebhookProvider]
  )
  lazy val integrityChecks: Set[GenIntegrityCheckOps] = Set(
    wire[ProfileIntegrityCheckOps],
    wire[OrganisationIntegrityCheckOps],
    wire[TagIntegrityCheckOps],
    wire[UserIntegrityCheckOps],
    wire[ImpactStatusIntegrityCheckOps],
    wire[ResolutionStatusIntegrityCheckOps],
    wire[ObservableTypeIntegrityCheckOps],
    wire[CustomFieldIntegrityCheckOps],
    wire[CaseTemplateIntegrityCheckOps],
    wire[DataIntegrityCheckOps],
    wire[CaseIntegrityCheckOps],
    wire[AlertIntegrityCheckOps]
  )

  lazy val entrypoint: Entrypoint     = wire[Entrypoint]
  val errorHandler: ErrorHandler.type = ErrorHandler

  val defaultAction: ActionFunction[Request, AuthenticatedRequest] = new ActionFunction[Request, AuthenticatedRequest] {
    override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
      Future.failed(NotFoundError(request.path))
    override protected def executionContext: ExecutionContext = scalligraphApplication.executionContext
  }

  override def routers: Set[Router] =
    Set(
      SimpleRouter {
        case POST(p"/api/v${int(version)}/query") =>
          val queryExecutor = scalligraphApplication.getQueryExecutor(version)
          entrypoint("query")
            .extract("query", queryExecutor.parser.on("query"))
            .auth { request =>
              queryExecutor.execute(request.body("query"), request)
            }
      }
    )

  override def queryExecutors: Set[QueryExecutor] = Set.empty
  override def schemas: Set[UpdatableSchema]      = Set(TheHiveSchemaDefinition)
}
