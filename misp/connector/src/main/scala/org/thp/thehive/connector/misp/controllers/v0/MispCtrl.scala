package org.thp.thehive.connector.misp.controllers.v0

import akka.actor.ActorRef
import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.misp.services.{MispActor, MispExportSrv}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.{AlertSrv, CaseSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class MispCtrl @Inject() (
    entrypoint: Entrypoint,
    mispExportSrv: MispExportSrv,
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    @Named("with-thehive-schema") db: Database,
    @Named("misp-actor") mispActor: ActorRef,
    implicit val ec: ExecutionContext
) {

  def sync: Action[AnyContent] =
    entrypoint("sync MISP events")
      .authPermitted(Permissions.manageOrganisation) { _ =>
        mispActor ! MispActor.Synchro
        Success(Results.NoContent)
      }

  def exportCase(mispId: String, caseIdOrNumber: String): Action[AnyContent] =
    entrypoint("export case into MISP")
      .asyncAuth { implicit authContext =>
        for {
          c <- Future.fromTry(db.roTransaction { implicit graph =>
            caseSrv
              .get(EntityIdOrName(caseIdOrNumber))
              .can(Permissions.manageShare)
              .getOrFail("Case")
          })
          _ <- mispExportSrv.export(mispId, c)
        } yield Results.NoContent
      }

  def cleanMispAlerts: Action[AnyContent] =
    entrypoint("clean MISP alerts")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .startTraversal
          .filterByType("misp")
          .visible
          .toIterator
          .toTry(alertSrv.remove(_))
          .map(_ => Results.NoContent)
      }
}
