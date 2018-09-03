package org.thp.thehive
import play.api.{Configuration, Environment, Logger}

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.thp.scalligraph.auth.AuthSrv
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{Database, Schema, SchemaChecker}
import org.thp.scalligraph.neo4j.Neo4jDatabase
import org.thp.scalligraph.orientdb.OrientDatabase
import org.thp.scalligraph.services.auth.{ADAuthSrv, LdapAuthSrv, MultiAuthSrv}
import org.thp.thehive.models.TheHiveSchema
import org.thp.thehive.services.LocalAuthSrv

class TheHiveModule(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule /*with AkkaGuiceSupport*/ {
  lazy val logger = Logger(getClass)

  override def configure(): Unit = {
    bind[org.thp.scalligraph.auth.UserSrv].to[org.thp.thehive.services.LocalUserSrv]

    val authBindings = ScalaMultibinder.newSetBinder[AuthSrv](binder)
    configuration
      .getOptional[Seq[String]]("auth.provider")
      .getOrElse(Seq("local"))
      .foreach {
        case "ad"    ⇒ authBindings.addBinding.to[ADAuthSrv]
        case "ldap"  ⇒ authBindings.addBinding.to[LdapAuthSrv]
        case "local" ⇒ authBindings.addBinding.to[LocalAuthSrv]
        case other   ⇒ logger.error(s"Authentication provider [$other] is not recognized")
      }
    bind[AuthSrv].to[MultiAuthSrv]

    configuration.get[String]("db.provider") match {
      case "janusgraph" ⇒ bind[Database].to[JanusDatabase]
      case "neo4j"      ⇒ bind[Database].to[Neo4jDatabase]
      case "orientdb"   ⇒ bind[Database].to[OrientDatabase]
      case other        ⇒ sys.error(s"Authentication provider [$other] is not recognized")
    }

    bind[Schema].to[TheHiveSchema]
    bind[Int].annotatedWith(Names.named("schemaVersion")).toInstance(1)
    bind[SchemaChecker].asEagerSingleton()

//      if (environment.mode == Mode.Prod)
//        bind[AssetCtrl].to[AssetCtrlProd]
//      else
//        bind[AssetCtrl].to[AssetCtrlDev]

//      ScalaMultibinder.newSetBinder[Connector](binder)
    ()
  }
}
