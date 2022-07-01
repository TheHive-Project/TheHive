package org.thp.thehive

import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.thp.scalligraph.SingleInstance
import org.thp.scalligraph.auth._
import org.thp.scalligraph.janus.{ImmenseTermProcessor, JanusDatabaseProvider}
import org.thp.scalligraph.models.{Database, UpdatableSchema}
import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{IntegrityCheck, _}
import org.thp.thehive.controllers.v0.{QueryExecutorVersion0Provider, TheHiveQueryExecutor => TheHiveQueryExecutorV0}
import org.thp.thehive.controllers.v1.{TheHiveQueryExecutor => TheHiveQueryExecutorV1}
import org.thp.thehive.models.{TheHiveSchemaDefinition, UseHashToIndex}
import org.thp.thehive.services.notification.NotificationActor
import org.thp.thehive.services.notification.notifiers._
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.{Connector, LocalKeyAuthProvider, LocalPasswordAuthProvider, LocalUserSrv, UserSrv => _, _}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

import javax.inject.{Inject, Provider, Singleton}

class TheHiveModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger: Logger = Logger(getClass)

  override def configure(): Unit = {
//    bind[UserSrv].to[LocalUserSrv]
    bind(classOf[UserSrv]).to(classOf[LocalUserSrv])
//    bind[AuthSrv].toProvider[MultiAuthSrvProvider]
    bind(classOf[AuthSrv]).toProvider(classOf[TOTPAuthSrvProvider])

    val authBindings = ScalaMultibinder.newSetBinder[AuthSrvProvider](binder)
    authBindings.addBinding.to[ADAuthProvider]
    authBindings.addBinding.to[LdapAuthProvider]
    authBindings.addBinding.to[LocalPasswordAuthProvider]
    authBindings.addBinding.to[LocalKeyAuthProvider]
    authBindings.addBinding.to[BasicAuthProvider]
    authBindings.addBinding.to[HeaderAuthProvider]
    authBindings.addBinding.to[PkiAuthProvider]
    authBindings.addBinding.to[SessionAuthProvider]
    authBindings.addBinding.to[OAuth2Provider]

    val triggerBindings = ScalaMultibinder.newSetBinder[TriggerProvider](binder)
    triggerBindings.addBinding.to[AlertCreatedProvider]
    triggerBindings.addBinding.to[AnyEventProvider]
    triggerBindings.addBinding.to[CaseCreatedProvider]
    triggerBindings.addBinding.to[FilteredEventProvider]
    triggerBindings.addBinding.to[JobFinishedProvider]
    triggerBindings.addBinding.to[LogInMyTaskProvider]
    triggerBindings.addBinding.to[TaskAssignedProvider]
    triggerBindings.addBinding.to[CaseShareProvider]

    val notifierBindings = ScalaMultibinder.newSetBinder[NotifierProvider](binder)
    notifierBindings.addBinding.to[AppendToFileProvider]
    notifierBindings.addBinding.to[EmailerProvider]
    notifierBindings.addBinding.to[MattermostProvider]
    notifierBindings.addBinding.to[WebhookProvider]

    configuration.get[String]("db.provider") match {
      case "janusgraph" => bind[Database].toProvider[JanusDatabaseProvider]
      case other        => sys.error(s"Authentication provider [$other] is not recognized")
    }

    configuration.get[String]("storage.provider") match {
      case "localfs"  => bind(classOf[StorageSrv]).to(classOf[LocalFileSystemStorageSrv])
      case "database" => bind(classOf[StorageSrv]).to(classOf[DatabaseStorageSrv])
      case "hdfs"     => bind(classOf[StorageSrv]).to(classOf[HadoopStorageSrv])
      case "s3"       => bind(classOf[StorageSrv]).to(classOf[S3StorageSrv])
      case other      => sys.error(s"Storage provider [$other] is not recognized")
    }

    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[TheHiveRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV0]
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV1]
    bind[QueryExecutor].annotatedWithName("v0").toProvider[QueryExecutorVersion0Provider]
    ScalaMultibinder.newSetBinder[Connector](binder)
    val schemaBindings = ScalaMultibinder.newSetBinder[UpdatableSchema](binder)
    schemaBindings.addBinding.to[TheHiveSchemaDefinition]

    bindActor[ConfigActor]("config-actor")
    bindActor[NotificationActor]("notification-actor")

    val integrityChecksBindings = ScalaMultibinder.newSetBinder[IntegrityCheck](binder)
    integrityChecksBindings.addBinding.to[ProfileIntegrityCheck]
    integrityChecksBindings.addBinding.to[OrganisationIntegrityCheck]
    integrityChecksBindings.addBinding.to[TagIntegrityCheck]
    integrityChecksBindings.addBinding.to[UserIntegrityCheck]
    integrityChecksBindings.addBinding.to[ImpactStatusIntegrityCheck]
    integrityChecksBindings.addBinding.to[ResolutionStatusIntegrityCheck]
    integrityChecksBindings.addBinding.to[ObservableTypeIntegrityCheck]
    integrityChecksBindings.addBinding.to[CustomFieldIntegrityCheck]
    integrityChecksBindings.addBinding.to[CaseTemplateIntegrityCheck]
    integrityChecksBindings.addBinding.to[DataIntegrityCheck]
    integrityChecksBindings.addBinding.to[CaseIntegrityCheck]
    integrityChecksBindings.addBinding.to[AlertIntegrityCheck]
    integrityChecksBindings.addBinding.to[TaskIntegrityCheck]
    integrityChecksBindings.addBinding.to[ObservableIntegrityCheck]
    integrityChecksBindings.addBinding.to[LogIntegrityCheck]
    integrityChecksBindings.addBinding.to[RoleIntegrityCheck]
    bind[TypedActorRef[IntegrityCheck.Request]].toProvider[IntegrityCheckActorProvider].asEagerSingleton()
    bind[TypedActorRef[CaseNumberActor.Request]].toProvider[CaseNumberActorProvider]

    bind[Scheduler].toProvider[QuartzSchedulerProvider].asEagerSingleton()

    bind[ActorRef].annotatedWithName("flow-actor").toProvider[FlowActorProvider]

    bind[SingleInstance].to[ClusterSetup].asEagerSingleton()

    ImmenseTermProcessor.registerStrategy("observableHashToIndex", _ => UseHashToIndex)
    ()
  }
}

@Singleton
class QuartzSchedulerProvider @Inject() (actorSystem: ActorSystem) extends Provider[Scheduler] {
  override def get(): Scheduler = {
    val factory = new StdSchedulerFactory
    factory.initialize()
    val scheduler = factory.getScheduler()
    actorSystem.registerOnTermination(scheduler.shutdown())
    scheduler.start()
    scheduler
  }
}
