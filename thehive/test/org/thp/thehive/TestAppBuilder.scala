package org.thp.thehive

import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth._
import org.thp.scalligraph.models.{DatabaseProvider, Schema}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.TheHiveSchema
import org.thp.thehive.services.notification._
import org.thp.thehive.services.notification.email.EmailerProvider
import org.thp.thehive.services.notification.triggers.{LogInMyTaskProvider, TriggerProvider}
import org.thp.thehive.services.{LocalKeyAuthProvider, LocalPasswordAuthProvider, LocalUserSrv}

object TestAppBuilder {

  def apply(dbProvider: DatabaseProvider): AppBuilder =
    (new AppBuilder)
      .bindToProvider(dbProvider)
      .bind[UserSrv, LocalUserSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .multiBind[AuthSrvProvider](classOf[LocalPasswordAuthProvider], classOf[LocalKeyAuthProvider], classOf[HeaderAuthProvider])
      .multiBind[NotifierProvider](classOf[AppendToFileProvider])
      .multiBind[NotifierProvider](classOf[EmailerProvider])
      .multiBind[TriggerProvider](classOf[LogInMyTaskProvider])
      .bindToProvider[AuthSrv, MultiAuthSrvProvider]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[NotificationActor]("notification-actor")
      .addConfiguration("auth.providers = [{name:local},{name:key},{name:header, userHeader:user}]")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
      .addConfiguration("play.mailer.mock = yes")
      .addConfiguration("play.mailer.debug = yes")
}
