package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
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
    entryPoint("create case share")
      .extract("share", FieldsParser[InputShare])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputShare: InputShare = request.body("share")
        for {
          _ <- userSrv.current.can(Permissions.manageShare).existsOrFail()
          organisation <- organisationSrv
            .get(request.organisation)
            .visibleOrganisations
            .get(inputShare.organisationName)
            .getOrFail()
          case0     <- caseSrv.getOrFail(caseId)
          profile   <- profileSrv.getOrFail(inputShare.profile)
          share     <- shareSrv.shareCase(case0, organisation, profile)
          richShare <- shareSrv.get(share).richShare.getOrFail()
          _         <- if (inputShare.tasks == TasksFilter.all) shareSrv.shareCaseTasks(share) else Success(Nil)
          _         <- if (inputShare.observables == ObservablesFilter.all) shareSrv.shareCaseObservables(share) else Success(Nil)
        } yield Results.Created(richShare.toJson)
      }

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
}
