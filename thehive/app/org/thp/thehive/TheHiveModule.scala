package org.thp.thehive

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth._
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.orientdb.{OrientDatabase, OrientDatabaseStorageSrv}
import org.thp.scalligraph.services.{DatabaseStorageSrv, LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.SchemaUpdater
import org.thp.thehive.services.{LocalKeyAuthSrv, LocalUserSrv}
//import org.thp.scalligraph.neo4j.Neo4jDatabase
//import org.thp.scalligraph.orientdb.OrientDatabase
import play.api.routing.{Router ⇒ PlayRouter}
import play.api.{Configuration, Environment, Logger}

import org.thp.scalligraph.query.QueryExecutor
import org.thp.scalligraph.services.auth.{ADAuthSrv, LdapAuthSrv}
import org.thp.thehive.controllers.v0.{TheHiveQueryExecutor ⇒ TheHiveQueryExecutorV0}
import org.thp.thehive.controllers.v1.{TheHiveQueryExecutor ⇒ TheHiveQueryExecutorV1}
import org.thp.thehive.services.LocalPasswordAuthSrv

class TheHiveModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
//    bind[UserSrv].to[LocalUserSrv]
    bind(classOf[UserSrv]).to(classOf[LocalUserSrv])
//    bind[AuthSrv].toProvider[MultuAuthSrvProvider]
    bind(classOf[AuthSrv]).toProvider(classOf[MultiAuthSrvProvider])

    val authBindings = ScalaMultibinder.newSetBinder[AuthSrv](binder)
    configuration
      .get[Seq[String]]("auth.provider")
      .foreach {
        case "ad"    ⇒ authBindings.addBinding.to[ADAuthSrv]
        case "ldap"  ⇒ authBindings.addBinding.to[LdapAuthSrv]
        case "local" ⇒ authBindings.addBinding.to[LocalPasswordAuthSrv]
        case "key"   ⇒ authBindings.addBinding.to[LocalKeyAuthSrv]
        case other   ⇒ logger.error(s"Authentication provider [$other] is not recognized")
      }

    configuration.get[String]("db.provider") match {
      case "janusgraph" ⇒ bind(classOf[Database]).to(classOf[JanusDatabase])
//      case "neo4j"      ⇒ bind(classOf[Database]).to(classOf[Neo4jDatabase])
      case "orientdb" ⇒ bind(classOf[Database]).to(classOf[OrientDatabase])
      case other      ⇒ sys.error(s"Authentication provider [$other] is not recognized")
    }

    configuration.get[String]("storage.provider") match {
      case "localfs"  ⇒ bind(classOf[StorageSrv]).to(classOf[LocalFileSystemStorageSrv])
      case "database" ⇒ bind(classOf[StorageSrv]).to(classOf[DatabaseStorageSrv])
      case "orientdb" ⇒ bind(classOf[StorageSrv]).to(classOf[OrientDatabaseStorageSrv])
    }

    val routerBindings = ScalaMultibinder.newSetBinder[PlayRouter](binder)
    routerBindings.addBinding.toProvider[TheHiveRouter]
    val queryExecutorBindings = ScalaMultibinder.newSetBinder[QueryExecutor](binder)
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV0]
    queryExecutorBindings.addBinding.to[TheHiveQueryExecutorV1]

    bind(classOf[SchemaUpdater]).asEagerSingleton()
    ()
  }
}
