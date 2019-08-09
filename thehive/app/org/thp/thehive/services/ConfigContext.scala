package org.thp.thehive.services

import scala.util.Try

import play.api.libs.json.JsValue

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ConfigContext

@Singleton
class UserConfigContext @Inject()(db: Database, configSrv: ConfigSrv) extends ConfigContext[AuthContext] {
  override def defaultPath(path: String): String = s"user.defaults.$path"

  override def getValue(context: AuthContext, path: String): Option[JsValue] =
    db.roTransaction { implicit graph =>
      configSrv
        .user
        .getConfigValue(context.userId, path)
        .orElse(
          configSrv
            .organisation
            .getConfigValue(context.organisation, s"users.$path")
        )
        .map(_.value)
    }

  override def setValue(context: AuthContext, path: String, value: JsValue): Try[String] =
    db.tryTransaction(
      graph =>
        configSrv
          .user
          .setConfigValue(context.userId, path, value)(graph, context)
          .map(_ => s"user.${context.userId}.$path")
    )
}

@Singleton
class OrganisationConfigContext @Inject()(db: Database, configSrv: ConfigSrv) extends ConfigContext[AuthContext] {
  override def defaultPath(path: String): String = s"organisation.defaults.$path"

  override def getValue(context: AuthContext, path: String): Option[JsValue] =
    db.roTransaction { implicit graph =>
      configSrv
        .organisation
        .getConfigValue(context.organisation, path)
        .orElse(
          configSrv
            .organisation
            .getConfigValue("defaults", path)
        )
        .map(_.value)
    }

  override def setValue(context: AuthContext, path: String, value: JsValue): Try[String] = db.tryTransaction(
    graph =>
      configSrv
        .organisation
        .setConfigValue(context.organisation, path, value)(graph, context)
        .map(_ => s"organisation.${context.organisation}.$path")
  )
}
