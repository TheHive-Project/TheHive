package org.thp.thehive.cloner

import akka.actor.typed.scaladsl.adapter.{ClassicActorRefOps, ClassicActorSystemOps}
import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services._
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigTag}
import org.thp.thehive.migration.th4.DummyActor
import org.thp.thehive.services._
import org.thp.thehive.services.notification.NotificationTag
import play.api.Configuration
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.cache.{DefaultSyncCacheApi, SyncCacheApi}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class IntegrityCheckApp(val configuration: Configuration, val db: Database)(implicit
    val actorSystem: ActorSystem
) extends MapMerger
    with CaffeineCacheComponents {
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  val executionContext: ExecutionContext = actorSystem.dispatcher
  val mat: Materializer                  = Materializer.matFromSystem
  lazy val cache: SyncCacheApi = defaultCacheApi.sync match {
    case sync: SyncCacheApi => sync
    case _                  => new DefaultSyncCacheApi(defaultCacheApi)
  }
  lazy val storageSrv: StorageSrv = configuration.get[String]("storage.provider") match {
    case "localfs" => new LocalFileSystemStorageSrv(configuration)
    case "hdfs"    => new HadoopStorageSrv(configuration)
    case "s3" =>
      new S3StorageSrv(
        configuration,
        actorSystem,
        executionContext,
        mat
      )
    case other => sys.error(s"Storage provider $other is not supported")
  }
  lazy val caseNumberActor: TypedActorRef[CaseNumberActor.Request] =
    actorSystem.spawn(CaseNumberActor.behavior(db, applicationConfig, caseSrv), "case-number-actor")
  lazy val profileSrv: ProfileSrv                   = wire[ProfileSrv]
  lazy val organisationSrv: OrganisationSrv         = wire[OrganisationSrv]
  lazy val tagSrv: TagSrv                           = wire[TagSrv]
  lazy val userSrv: UserSrv                         = wire[UserSrv]
  lazy val impactStatusSrv: ImpactStatusSrv         = wire[ImpactStatusSrv]
  lazy val resolutionStatusSrv: ResolutionStatusSrv = wire[ResolutionStatusSrv]
  lazy val observableTypeSrv: ObservableTypeSrv     = wire[ObservableTypeSrv]
  lazy val customFieldSrv: CustomFieldSrv           = wire[CustomFieldSrv]
  lazy val customFieldValueSrv: CustomFieldValueSrv = wire[CustomFieldValueSrv]
  lazy val caseTemplateSrv: CaseTemplateSrv         = wire[CaseTemplateSrv]
  lazy val applicationConfig: ApplicationConfig     = wire[ApplicationConfig]
  lazy val eventSrv: EventSrv                       = wire[EventSrv]

  lazy val dummyActor: ActorRef                                       = wireAnonymousActor[DummyActor]
  lazy val auditSrv: AuditSrv                                         = wire[AuditSrv]
  lazy val roleSrv: RoleSrv                                           = wire[RoleSrv]
  lazy val integrityCheckActor: TypedActorRef[IntegrityCheck.Request] = dummyActor.toTyped
  lazy val configActor: ActorRef @@ ConfigTag                         = dummyActor.taggedWith[ConfigTag]
  lazy val notificationActor: ActorRef @@ NotificationTag             = dummyActor.taggedWith[NotificationTag]
  lazy val taxonomySrv: TaxonomySrv                                   = wire[TaxonomySrv]
  lazy val attachmentSrv: AttachmentSrv                               = wire[AttachmentSrv]
  lazy val logSrv: LogSrv                                             = wire[LogSrv]
  lazy val taskSrv: TaskSrv                                           = wire[TaskSrv]
  lazy val shareSrv: ShareSrv                                         = wire[ShareSrv]
  lazy val caseSrv: CaseSrv                                           = wire[CaseSrv]
  lazy val observableSrv: ObservableSrv                               = wire[ObservableSrv]
  lazy val dataSrv: DataSrv                                           = wire[DataSrv]
  lazy val alertSrv: AlertSrv                                         = wire[AlertSrv]
  integrityCheckOpsBindings.addBinding.to[RoleIntegrityCheck]

  lazy val checks = Seq(
    wire[AlertIntegrityCheck],
    wire[CaseIntegrityCheck],
    wire[CaseTemplateIntegrityCheck],
    wire[CustomFieldIntegrityCheck],
    wire[DataIntegrityCheck],
    wire[ImpactStatusIntegrityCheck],
    wire[LogIntegrityCheck],
    wire[ObservableIntegrityCheck],
    wire[ObservableTypeIntegrityCheck],
    wire[OrganisationIntegrityCheck],
    wire[ProfileIntegrityCheck],
    wire[ResolutionStatusIntegrityCheck],
    wire[TagIntegrityCheck],
    wire[TaskIntegrityCheck],
    wire[UserIntegrityCheck]
  )

  def runChecks(): Unit = {
    implicit val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
    checks
      .foreach { c =>
        println(s"Running check on ${c.name} ...")
        val desupStats = c match {
          case dc: DedupCheck[_] => dc.dedup(KillSwitch.alwaysOn)
          case _                 => Map.empty[String, Long]
        }
        val globalStats = c match {
          case gc: GlobalCheck[_] => gc.runGlobalCheck(24.hours, KillSwitch.alwaysOn)
          case _                  => Map.empty[String, Long]
        }
        val statsStr = (desupStats <+> globalStats)
          .collect {
            case (k, v) if v != 0 => s"$k:$v"
          }
          .mkString(" ")
        if (statsStr.isEmpty)
          println("  no change needed")
        else
          println(s"  $statsStr")

      }
  }
}
