package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputShare, ObservablesFilter, TasksFilter}
import org.thp.thehive.models.{Organisation, Permissions}
import org.thp.thehive.services._

@Singleton
class ShareCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    shareSrv: ShareSrv,
    organisationSrv: OrganisationSrv,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    profileSrv: ProfileSrv,
    userSrv: UserSrv
) {

  def shareCase(caseId: String): Action[AnyContent] =
    entryPoint("create case shares")
      .extract("shares", FieldsParser[InputShare].sequence.on("shares"))
      .authTransaction(db) { implicit request => implicit graph =>
        val inputShares: Seq[InputShare] = request.body("shares")
        if (userSrv.current.can(Permissions.manageShare).existsOrFail().isSuccess) {
          // No more magic removal atm
//          caseSrv
//            .get(caseId)
//            .shares
//            .richShare
//            .toList
//            .filter(
//              rs =>
//                rs.organisationName != request.organisation && !inputShares
//                  .exists(is => is.profile == rs.profileName && is.organisationName == rs.organisationName)
//            )
//            .foreach(rs => shareSrv.get(rs.share).remove())

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
    } yield richShare

  def removeShare(id: String): Action[AnyContent] =
    entryPoint("remove share")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          organisation <- userSrv.current.organisations(Permissions.manageShare).get(request.organisation).getOrFail()
          _            <- removeShare(id, organisation, None)
        } yield Results.NoContent
      }

  private def removeShare(id: String, organisation: Organisation with Entity, entity: Option[String])(
      implicit graph: Graph,
      authContext: AuthContext
  ) =
    for {
      relatedOrg <- shareSrv.get(id).organisation.getOrFail()
      if relatedOrg.name != organisation.name
      share <- shareSrv.get(id).getOrFail()
      _ = entity.map {
        case "task"       => shareSrv.removeShareTasks(share)
        case "observable" => shareSrv.removeShareObservable(share)
        case _            => shareSrv.remove(share)
      } getOrElse shareSrv.remove(share)
    } yield ()

  def removeShares(): Action[AnyContent] =
    entryPoint("remove share")
      .extract("shares", FieldsParser[String].sequence.on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val shareIds: Seq[String] = request.body("shares")

        userSrv.current.organisations(Permissions.manageShare).get(request.organisation).getOrFail().flatMap { organisation =>
          shareIds
            .toTry(id => removeShare(id, organisation, None))
            .map(_ => Results.NoContent)
        }
      }

  def removeShareTasks(): Action[AnyContent] =
    entryPoint("remove share tasks")
      .extract("shares", FieldsParser[String].sequence.on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val shareIds: Seq[String] = request.body("shares")

        userSrv.current.organisations(Permissions.manageShare).get(request.organisation).getOrFail().flatMap { organisation =>
          shareIds
            .toTry(id => removeShare(id, organisation, Some("task")))
            .map(_ => Results.NoContent)
        }
      }

  def removeShareObservables(): Action[AnyContent] =
    entryPoint("remove share observables")
      .extract("shares", FieldsParser[String].sequence.on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val shareIds: Seq[String] = request.body("shares")

        userSrv.current.organisations(Permissions.manageShare).get(request.organisation).getOrFail().flatMap { organisation =>
          shareIds
            .toTry(id => removeShare(id, organisation, Some("observable")))
            .map(_ => Results.NoContent)
        }
      }

  def updateShare(id: String): Action[AnyContent] =
    entryPoint("update share")
      .extract("profile", FieldsParser.string.on("profile"))
      .authTransaction(db) { implicit request => implicit graph =>
        val profile: String = request.body("profile")
        for {
          _         <- userSrv.current.organisations(Permissions.manageShare).get(request.organisation).getOrFail()
          richShare <- shareSrv.get(id).richShare.getOrFail()
          _ <- organisationSrv
            .get(request.organisation)
            .visibleOrganisations
            .get(richShare.organisationName)
            .getOrFail()
          p <- profileSrv.getOrFail(profile)
          _ <- shareSrv.update(p, richShare.share)
        } yield Results.Ok
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
                  .filter(_.organisation.hasNot(Key("name"), P.eq(request.organisation)))
                  .richShare
                  .toList
                  .map(_.toJson)
              )
            )
          )
        } else Success(Results.Forbidden)
      }

  def listShareTasks(caseId: String, taskId: String): Action[AnyContent] =
    entryPoint("list task shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageShare)) {
          Success(
            Results.Ok(
              JsArray(
                caseSrv
                  .get(caseId)
                  .shares
                  .filter(_.organisation.hasNot(Key("name"), P.eq(request.organisation)))
                  .byTask(taskId)
                  .richShare
                  .toList
                  .map(_.toJson)
              )
            )
          )
        } else Success(Results.Forbidden)
      }

  def listShareObservables(caseId: String, obsId: String): Action[AnyContent] =
    entryPoint("list observable shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageShare)) {
          Success(
            Results.Ok(
              JsArray(
                caseSrv
                  .get(caseId)
                  .shares
                  .filter(_.organisation.hasNot(Key("name"), P.eq(request.organisation)))
                  .byObservable(obsId)
                  .richShare
                  .toList
                  .map(_.toJson)
              )
            )
          )
        } else Success(Results.Forbidden)
      }

  def shareTask(taskId: String): Action[AnyContent] =
    entryPoint("share task")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisationIds: Seq[String] = request.body("organisations")
        for {
          task           <- taskSrv.getOrFail(taskId)
          organisations  <- organisationIds.toTry(organisationSrv.getOrFail)
          myOrganisation <- organisationSrv.getOrFail(request.organisation)
          _              <- shareSrv.updateTaskShares(task, organisations :+ myOrganisation)
        } yield Results.NoContent
      }

  def shareObservable(observableId: String): Action[AnyContent] =
    entryPoint("share observable")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisationIds: Seq[String] = request.body("organisations")
        for {
          observable     <- observableSrv.getOrFail(observableId)
          organisations  <- organisationIds.toTry(organisationSrv.getOrFail)
          myOrganisation <- organisationSrv.getOrFail(request.organisation)
          _              <- shareSrv.updateObservableShares(observable, organisations :+ myOrganisation)
        } yield Results.NoContent
      }

}
