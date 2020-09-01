package org.thp.thehive

import akka.actor.ActorRef
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth._
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.services.{GenIntegrityCheckOps, HadoopStorageSrv, S3StorageSrv}
import org.thp.thehive.models.{DatabaseProvider, TheHiveSchemaDefinition}
import org.thp.thehive.services.notification.notifiers._
import org.thp.thehive.services.notification.triggers._
import org.thp.thehive.services.{UserSrv => _, _}
import play.api.libs.concurrent.AkkaGuiceSupport
//import org.thp.scalligraph.orientdb.{OrientDatabase, OrientDatabaseStorageSrv}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{DatabaseStorageSrv, LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.services.notification.NotificationActor
import org.thp.thehive.services.{Connector, LocalKeyAuthProvider, LocalPasswordAuthProvider, LocalUserSrv}
//import org.thp.scalligraph.neo4j.Neo4jDatabase
//import org.thp.scalligraph.orientdb.OrientDatabase
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.controllers.v0.{TheHiveQueryExecutor => TheHiveQueryExecutorV0}
import org.thp.thehive.controllers.v1.{TheHiveQueryExecutor => TheHiveQueryExecutorV1}
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

class TheHiveModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger: Logger = Logger(getClass)

  override def configure(): Unit = {
//    bind[UserSrv].to[LocalUserSrv]
    bind(classOf[UserSrv]).to(classOf[LocalUserSrv])
//    bind[AuthSrv].toProvider[MultuAuthSrvProvider]
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

    val notifierBindings = ScalaMultibinder.newSetBinder[NotifierProvider](binder)
    notifierBindings.addBinding.to[AppendToFileProvider]
    notifierBindings.addBinding.to[EmailerProvider]
    notifierBindings.addBinding.to[MattermostProvider]
    notifierBindings.addBinding.to[WebhookProvider]

    configuration.get[String]("db.provider") match {
      case "janusgraph" => bind[Database].to[JanusDatabase]
      case other        => sys.error(s"Authentication provider [$other] is not recognized")
    }
    bind[Database].annotatedWithName("with-thehive-schema").toProvider[DatabaseProvider]

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
    ScalaMultibinder.newSetBinder[Connector](binder)
    val schemaBindings = ScalaMultibinder.newSetBinder[Schema](binder)
    schemaBindings.addBinding.to[TheHiveSchemaDefinition]

    bindActor[ConfigActor]("config-actor")
    bindActor[NotificationActor]("notification-actor")

    val integrityCheckOpsBindings = ScalaMultibinder.newSetBinder[GenIntegrityCheckOps](binder)
    integrityCheckOpsBindings.addBinding.to[ProfileIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[OrganisationIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[TagIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[UserIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[ImpactStatusIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[ResolutionStatusIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[ObservableTypeIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[CustomFieldIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[CaseTemplateIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[DataIntegrityCheckOps]
    integrityCheckOpsBindings.addBinding.to[CaseIntegrityCheckOps]
    bind[ActorRef].annotatedWithName("integrity-check-actor").toProvider[IntegrityCheckActorProvider]

    bind[ActorRef].annotatedWithName("flow-actor").toProvider[FlowActorProvider]

    bind[ClusterSetup].asEagerSingleton()
    ()
  }
}
