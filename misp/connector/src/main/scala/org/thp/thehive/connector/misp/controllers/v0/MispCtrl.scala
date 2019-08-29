package org.thp.thehive.connector.misp.controllers.v0

import scala.util.Success

import play.api.mvc.{Action, AnyContent, Results}

import akka.actor.ActorRef
import com.google.inject.name.Named
import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.misp.services.MispActor
import org.thp.thehive.services.AlertSrv

@Singleton
class MispCtrl @Inject()(entryPoint: EntryPoint, alertSrv: AlertSrv, db: Database, @Named("misp-actor") mispActor: ActorRef) {

  def sync: Action[AnyContent] =
    entryPoint("sync MISP events")
      .auth { _ =>
        println(s"sendding Synchro to ${mispActor}")
        mispActor ! MispActor.Synchro
        Success(Results.NoContent)
      }

  def cleanMispAlerts: Action[AnyContent] =
    entryPoint("clean MISP alerts")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .initSteps
          .has(Key("type"), P.eq("misp"))
          .toIterator
          .toTry(alertSrv.cascadeRemove(_))
          .map(_ => Results.NoContent)
      }
}
