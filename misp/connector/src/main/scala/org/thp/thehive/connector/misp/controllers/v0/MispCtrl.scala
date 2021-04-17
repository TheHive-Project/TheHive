package org.thp.thehive.connector.misp.controllers.v0

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.misp.services.{MispExportSrv, MispTag, Synchro}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.{AlertSrv, CaseSrv, OrganisationSrv}
import play.api.mvc.{Action, AnyContent, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class MispCtrl(
    entrypoint: Entrypoint,
    mispExportSrv: MispExportSrv,
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    db: Database,
    organisationSrv: OrganisationSrv,
    mispActor: ActorRef @@ MispTag,
    implicit val ec: ExecutionContext
) {

  def sync: Action[AnyContent] =
    entrypoint("sync MISP events")
      .authPermitted(Permissions.manageOrganisation) { _ =>
        mispActor ! Synchro
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
          .visible(organisationSrv)
          .toIterator
          .toTry(alertSrv.remove(_))
          .map(_ => Results.NoContent)
      }
}
