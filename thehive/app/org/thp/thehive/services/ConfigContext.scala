package org.thp.thehive.services

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ConfigContext
import play.api.libs.json.JsValue

import scala.util.Try

@Singleton
class UserConfigContext @Inject() (db: Database, configSrv: ConfigSrv) extends ConfigContext[AuthContext] {
  override def defaultPath(path: String): String = s"user.defaults.$path"

  override def getValue(context: AuthContext, path: String): Option[JsValue] =
    db.roTransaction { implicit graph =>
      configSrv
        .user
        .getConfigValue(EntityName(context.userId), path)
        .orElse(
          configSrv
            .organisation
            .getConfigValue(context.organisation, s"users.$path")
        )
        .map(_.value)
    }

  override def setValue(context: AuthContext, path: String, value: JsValue): Try[String] =
    db.tryTransaction(graph =>
      configSrv
        .user
        .setConfigValue(EntityName(context.userId), path, value)(graph, context)
        .map(_ => s"user.${context.userId}.$path")
    )
}

@Singleton
class OrganisationConfigContext @Inject() (db: Database, configSrv: ConfigSrv) extends ConfigContext[AuthContext] {
  override def defaultPath(path: String): String = s"organisation.defaults.$path"

  override def getValue(context: AuthContext, path: String): Option[JsValue] =
    db.roTransaction { implicit graph =>
      configSrv
        .organisation
        .getConfigValue(context.organisation, path)
        .orElse(
          configSrv
            .organisation
            .getConfigValue(EntityName("defaults"), path)
        )
        .map(_.value)
    }

  override def setValue(context: AuthContext, path: String, value: JsValue): Try[String] =
    db.tryTransaction(graph =>
      configSrv
        .organisation
        .setConfigValue(context.organisation, path, value)(graph, context)
        .map(_ => s"organisation.${context.organisation}.$path")
    )
}
