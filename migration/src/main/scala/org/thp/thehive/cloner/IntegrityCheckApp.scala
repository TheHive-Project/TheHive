package org.thp.thehive.cloner

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
import play.api.cache.SyncCacheApi

import scala.concurrent.ExecutionContext
import scala.util.Success

class IntegrityCheckApp(val configuration: Configuration, val db: Database)(implicit val actorSystem: ActorSystem) extends MapMerger {
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  val ec: ExecutionContext                          = actorSystem.dispatcher
  val mat: Materializer                             = Materializer.matFromSystem
  lazy val cache: SyncCacheApi                      = ???
  lazy val storageSrv: StorageSrv                   = ???
  lazy val profileSrv: ProfileSrv                   = wire[ProfileSrv]
  lazy val organisationSrv: OrganisationSrv         = wire[OrganisationSrv]
  lazy val tagSrv: TagSrv                           = wire[TagSrv]
  lazy val userSrv: UserSrv                         = wire[UserSrv]
  lazy val impactStatusSrv: ImpactStatusSrv         = wire[ImpactStatusSrv]
  lazy val resolutionStatusSrv: ResolutionStatusSrv = wire[ResolutionStatusSrv]
  lazy val observableTypeSrv: ObservableTypeSrv     = wire[ObservableTypeSrv]
  lazy val customFieldSrv: CustomFieldSrv           = wire[CustomFieldSrv]
  lazy val caseTemplateSrv: CaseTemplateSrv         = wire[CaseTemplateSrv]
  lazy val applicationConfig: ApplicationConfig     = wire[ApplicationConfig]
  lazy val eventSrv: EventSrv                       = wire[EventSrv]

  lazy val dummyActor: ActorRef                               = wireAnonymousActor[DummyActor]
  lazy val auditSrv: AuditSrv                                 = wire[AuditSrv]
  lazy val roleSrv: RoleSrv                                   = wire[RoleSrv]
  lazy val integrityCheckActor: ActorRef @@ IntegrityCheckTag = dummyActor.taggedWith[IntegrityCheckTag]
  lazy val configActor: ActorRef @@ ConfigTag                 = dummyActor.taggedWith[ConfigTag]
  lazy val notificationActor: ActorRef @@ NotificationTag     = dummyActor.taggedWith[NotificationTag]
  lazy val taxonomySrv: TaxonomySrv                           = wire[TaxonomySrv]
  lazy val attachmentSrv: AttachmentSrv                       = wire[AttachmentSrv]
  lazy val logSrv: LogSrv                                     = wire[LogSrv]
  lazy val taskSrv: TaskSrv                                   = wire[TaskSrv]
  lazy val shareSrv: ShareSrv                                 = wire[ShareSrv]
  lazy val caseSrv: CaseSrv                                   = wire[CaseSrv]
  lazy val observableSrv: ObservableSrv                       = wire[ObservableSrv]
  lazy val dataSrv: DataSrv                                   = wire[DataSrv]
  lazy val alertSrv: AlertSrv                                 = wire[AlertSrv]

  lazy val checks = Seq(
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
    wire[AlertIntegrityCheckOps],
    wire[TaskIntegrityCheckOps],
    wire[ObservableIntegrityCheckOps],
    wire[LogIntegrityCheckOps]
  )

  def runChecks(): Unit = {
    implicit val authContext: AuthContext = LocalUserSrv.getSystemAuthContext
    checks
      .foreach { c =>
        db.tryTransaction { implicit graph =>
          println(s"Running check on ${c.name} ...")
          c.initialCheck()
          val stats = c.duplicationCheck() <+> c.globalCheck()
          val statsStr = stats
            .collect {
              case (k, v) if v != 0 => s"$k:$v"
            }
            .mkString(" ")
          if (statsStr.isEmpty)
            println("  no change needed")
          else
            println(s"  $statsStr")
          Success(())
        }
      }
  }
}
