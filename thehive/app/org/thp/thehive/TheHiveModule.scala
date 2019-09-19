package org.thp.thehive

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth._
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.thehive.services.notification.mattermost.MattermostProvider
import org.thp.thehive.services.notification.triggers.{CaseCreatedProvider, LogInMyTaskProvider, TaskAssignedProvider, TriggerProvider}
import play.api.libs.concurrent.AkkaGuiceSupport
//import org.thp.scalligraph.orientdb.{OrientDatabase, OrientDatabaseStorageSrv}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{DatabaseStorageSrv, LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{SchemaUpdater, TheHiveSchema}
import org.thp.thehive.services.notification.email.EmailerProvider
import org.thp.thehive.services.notification.{AppendToFileProvider, NotificationActor, NotifierProvider}
import org.thp.thehive.services.{Connector, LocalKeyAuthProvider, LocalPasswordAuthProvider, LocalUserSrv}
//import org.thp.scalligraph.neo4j.Neo4jDatabase
//import org.thp.scalligraph.orientdb.OrientDatabase
import org.thp.scalligraph.query.QueryExecutor
import org.thp.thehive.controllers.v0.{TheHiveQueryExecutor => TheHiveQueryExecutorV0}
import org.thp.thehive.controllers.v1.{TheHiveQueryExecutor => TheHiveQueryExecutorV1}
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment, Logger}

class TheHiveModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
//    bind[UserSrv].to[LocalUserSrv]
    bind(classOf[UserSrv]).to(classOf[LocalUserSrv])
//    bind[AuthSrv].toProvider[MultuAuthSrvProvider]
    bind(classOf[AuthSrv]).toProvider(classOf[MultiAuthSrvProvider])

    val authBindings = ScalaMultibinder.newSetBinder[AuthSrvProvider](binder)
    authBindings.addBinding.to[ADAuthProvider]
    authBindings.addBinding.to[LdapAuthProvider]
    authBindings.addBinding.to[LocalPasswordAuthProvider]
    authBindings.addBinding.to[LocalKeyAuthProvider]
    authBindings.addBinding.to[BasicAuthProvider]
    authBindings.addBinding.to[HeaderAuthProvider]
    authBindings.addBinding.to[PkiAuthProvider]
    authBindings.addBinding.to[SessionAuthProvider]
    // TODO add more authSrv

    val triggerBindings = ScalaMultibinder.newSetBinder[TriggerProvider](binder)
    triggerBindings.addBinding.to[LogInMyTaskProvider]
    triggerBindings.addBinding.to[CaseCreatedProvider]
    triggerBindings.addBinding.to[TaskAssignedProvider]

    val notifierBindings = ScalaMultibinder.newSetBinder[NotifierProvider](binder)
    notifierBindings.addBinding.to[AppendToFileProvider]
    notifierBindings.addBinding.to[EmailerProvider]
    notifierBindings.addBinding.to[MattermostProvider]

    configuration.get[String]("db.provider") match {
      case "janusgraph" => bind(classOf[Database]).to(classOf[JanusDatabase])
//      case "neo4j"      => bind(classOf[Database]).to(classOf[Neo4jDatabase])
//      case "orientdb" => bind(classOf[Database]).to(classOf[OrientDatabase])
      case other => sys.error(s"Authentication provider [$other] is not recognized")
    }

    configuration.get[String]("storage.provider") match {
      case "localfs"  => bind(classOf[StorageSrv]).to(classOf[LocalFileSystemStorageSrv])
      case "database" => bind(classOf[StorageSrv]).to(classOf[DatabaseStorageSrv])
//      case "orientdb" => bind(classOf[StorageSrv]).to(classOf[OrientDatabaseStorageSrv])
    }

    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[TheHiveRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV0]
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV1]
    ScalaMultibinder.newSetBinder[Connector](binder)
    val schemaBindings = ScalaMultibinder.newSetBinder[Schema](binder)
    schemaBindings.addBinding.to[TheHiveSchema]

    bindActor[ConfigActor]("config-actor")
    bindActor[NotificationActor]("notification-actor")

    bind[SchemaUpdater].asEagerSingleton()
    ()
  }
}
