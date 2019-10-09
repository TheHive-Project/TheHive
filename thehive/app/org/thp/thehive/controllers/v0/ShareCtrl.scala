package org.thp.thehive.controllers.v0

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.{InputShare, ObservablesFilter, TasksFilter}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services._
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class ShareCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    shareSrv: ShareSrv,
    organisationSrv: OrganisationSrv,
    caseSrv: CaseSrv,
    profileSrv: ProfileSrv,
    userSrv: UserSrv
) {
  import ShareConversion._

  def shareCase(caseId: String): Action[AnyContent] =
    entryPoint("create case shares")
      .extract("shares", FieldsParser[InputShare].sequence.on("shares"))
      .authTransaction(db) { implicit request => implicit graph =>
        val inputShares: Seq[InputShare] = request.body("shares")
        if (userSrv.current.can(Permissions.manageShare).existsOrFail().isSuccess) {
          caseSrv
            .get(caseId)
            .shares
            .richShare
            .toList
            .filter(
              rs =>
                rs.organisationName != request.organisation && !inputShares
                  .exists(is => is.profile == rs.profileName && is.organisationName == rs.organisationName)
            )
            .foreach(rs => shareSrv.get(rs.share).remove())

          val (_, failures) = inputShares
            .map(is => share(is, request.organisation, caseId))
            .partition(_.isSuccess)
          if (failures.nonEmpty) Success(Results.InternalServerError(failures.map(_.failed.get).head.getMessage))
          else Success(Results.Created)
        } else Success(Results.Forbidden)
      }

  private def share(inputShare: InputShare, organisation: String, caseId: String)(implicit graph: Graph, authContext: AuthContext) =
    for {
      organisation <- organisationSrv
        .get(organisation)
        .visibleOrganisations
        .get(inputShare.organisationName)
        .getOrFail()
      case0     <- caseSrv.getOrFail(caseId)
      profile   <- profileSrv.getOrFail(inputShare.profile)
      share     <- shareSrv.shareCase(case0, organisation, profile)
      richShare <- shareSrv.get(share).richShare.getOrFail()
      _         <- if (inputShare.tasks == TasksFilter.all) shareSrv.shareCaseTasks(share) else Success(Nil)
      _         <- if (inputShare.observables == ObservablesFilter.all) shareSrv.shareCaseObservables(share) else Success(Nil)
    } yield richShare.toJson

  def listShareCases(caseId: String): Action[AnyContent] =
    entryPoint("list case shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageShare)) {
          Success(
            Results.Ok(
              JsArray(
                caseSrv
                  .get(caseId)
                  .shares
                  .richShare
                  .toList
                  .map(_.toJson)
              )
            )
          )
        } else Success(Results.Forbidden)
      }

  def listShareTasks(caseId: String, taskId: String): Action[AnyContent] =
    entryPoint("list case shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageShare)) {
          Success(
            Results.Ok(
              JsArray(
                caseSrv
                  .get(caseId)
                  .shares
                  .byTask(taskId)
                  .richShare
                  .toList
                  .map(_.toJson)
              )
            )
          )
        } else Success(Results.Forbidden)
      }
}
