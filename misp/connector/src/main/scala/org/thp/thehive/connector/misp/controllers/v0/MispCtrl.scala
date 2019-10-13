package org.thp.thehive.connector.misp.controllers.v0

import akka.actor.ActorRef
import com.google.inject.name.Named
import gremlin.scala.{Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.misp.services.{MispActor, MispExportSrv, MispImportSrv}
import org.thp.thehive.services.{AlertSrv, CaseSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class MispCtrl @Inject()(
    entryPoint: EntryPoint,
    mispImportSrv: MispImportSrv,
    mispExportSrv: MispExportSrv,
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    db: Database,
    @Named("misp-actor") mispActor: ActorRef,
    implicit val ec: ExecutionContext
) {

  def sync: Action[AnyContent] =
    entryPoint("sync MISP events")
      .auth { _ =>
        mispActor ! MispActor.Synchro
        Success(Results.NoContent)
      }

  def exportCase(mispId: String, caseIdOrNumber: String): Action[AnyContent] =
    entryPoint("export case into MISP")
      .asyncAuth { implicit authContext =>
        for {
          c <- Future.fromTry(db.roTransaction { implicit graph =>
            caseSrv
              .get(caseIdOrNumber)
              .getOrFail()
          })
          _ <- mispExportSrv.export(mispId, c)
        } yield Results.NoContent
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
