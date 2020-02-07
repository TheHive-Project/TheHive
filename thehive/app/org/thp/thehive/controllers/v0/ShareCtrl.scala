package org.thp.thehive.controllers.v0

import scala.util.{Failure, Success, Try}
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}
import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AuthorizationError, BadRequestError, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputShare, ObservablesFilter, TasksFilter}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services._

@Singleton
class ShareCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    shareSrv: ShareSrv,
    organisationSrv: OrganisationSrv,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    observableSrv: ObservableSrv,
    profileSrv: ProfileSrv
) {

  def shareCase(caseId: String): Action[AnyContent] =
    entrypoint("create case shares")
      .extract("shares", FieldsParser[InputShare].sequence.on("shares"))
      .authTransaction(db) { implicit request => implicit graph =>
        val inputShares: Seq[InputShare] = request.body("shares")
        caseSrv
          .get(caseId)
          .can(Permissions.manageShare)
          .getOrFail()
          .flatMap { `case` =>
            inputShares.toTry { inputShare =>
              for {
                organisation <- organisationSrv
                  .get(request.organisation)
                  .visibleOrganisationsFrom
                  .get(inputShare.organisationName)
                  .getOrFail()
                profile   <- profileSrv.getOrFail(inputShare.profile)
                share     <- shareSrv.shareCase(`case`, organisation, profile)
                richShare <- shareSrv.get(share).richShare.getOrFail()
                _         <- if (inputShare.tasks == TasksFilter.all) shareSrv.shareCaseTasks(share) else Success(Nil)
                _         <- if (inputShare.observables == ObservablesFilter.all) shareSrv.shareCaseObservables(share) else Success(Nil)
              } yield richShare.toJson
            }
          }
          .map(shares => Results.Ok(JsArray(shares)))
      }

  def removeShare(shareId: String): Action[AnyContent] =
    entrypoint("remove share")
      .authTransaction(db) { implicit request => implicit graph =>
        doRemoveShare(shareId).map(_ => Results.NoContent)
      }

  def removeShares(): Action[AnyContent] =
    entrypoint("remove share")
      .extract("shares", FieldsParser[String].sequence.on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val shareIds: Seq[String] = request.body("shares")
        shareIds.toTry(doRemoveShare(_)).map(_ => Results.NoContent)
      }

  def removeShares(caseId: String): Action[AnyContent] =
    entrypoint("remove share")
      .extract("organisations", FieldsParser[String].sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisations: Seq[String] = request.body("organisations")
        organisations
          .toTry { organisationId =>
            for {
              organisation <- organisationSrv.get(organisationId).getOrFail()
              _            <- if (organisation.name == request.organisation) Failure(BadRequestError("You cannot remove your own share")) else Success(())
              shareId      <- caseSrv.get(caseId).can(Permissions.manageShare).share(organisationId)._id.getOrFail()
              _            <- shareSrv.remove(shareId)
            } yield ()
          }
          .map(_ => Results.NoContent)
      }

  def removeTaskShares(taskId: String): Action[AnyContent] =
    entrypoint("remove share tasks")
      .extract("organisations", FieldsParser[String].sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisations: Seq[String] = request.body("organisations")

        taskSrv
          .getOrFail(taskId)
          .flatMap { task =>
            organisations.toTry { organisationName =>
              organisationSrv
                .getOrFail(organisationName)
                .flatMap(shareSrv.removeShareTasks(task, _))
            }
          }
          .map(_ => Results.NoContent)
      }

  def removeObservableShares(observableId: String): Action[AnyContent] =
    entrypoint("remove share observables")
      .extract("organisations", FieldsParser[String].sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisations: Seq[String] = request.body("organisations")

        observableSrv
          .getOrFail(observableId)
          .flatMap { observable =>
            organisations.toTry { organisationName =>
              organisationSrv
                .getOrFail(organisationName)
                .flatMap(shareSrv.removeShareObservable(observable, _))
            }
          }
          .map(_ => Results.NoContent)
      }

  private def doRemoveShare(shareId: String)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    if (!shareSrv.get(shareId).`case`.can(Permissions.manageShare).exists())
      Failure(AuthorizationError("You are not authorized to remove share"))
    else if (shareSrv.get(shareId).byOrganisationName(authContext.organisation).exists())
      Failure(AuthorizationError("You can't remove your share"))
    else
      shareSrv.remove(shareId)

  def updateShare(shareId: String): Action[AnyContent] =
    entrypoint("update share")
      .extract("profile", FieldsParser.string.on("profile"))
      .authTransaction(db) { implicit request => implicit graph =>
        val profile: String = request.body("profile")
        if (!shareSrv.get(shareId).`case`.can(Permissions.manageShare).exists())
          Failure(AuthorizationError("You are not authorized to remove share"))
        for {
          richShare <- shareSrv.get(shareId).richShare.getOrFail()
          _ <- organisationSrv
            .get(request.organisation)
            .visibleOrganisationsFrom
            .get(richShare.organisationName)
            .getOrFail()
          profile <- profileSrv.getOrFail(profile)
          _       <- shareSrv.update(richShare.share, profile)
        } yield Results.Ok
      }

  def listShareCases(caseId: String): Action[AnyContent] =
    entrypoint("list case shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        Success(
          Results.Ok(
            JsArray(
              caseSrv
                .get(caseId)
                .can(Permissions.manageShare)
                .shares
                .filter(_.organisation.hasNot("name", request.organisation).visible)
                .richShare
                .toList
                .map(_.toJson)
            )
          )
        )
      }

  def listShareTasks(caseId: String, taskId: String): Action[AnyContent] =
    entrypoint("list task shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        Success(
          Results.Ok(
            JsArray(
              caseSrv
                .get(caseId)
                .can(Permissions.manageShare)
                .shares
                .filter(_.organisation.hasNot("name", request.organisation).visible)
                .byTask(taskId)
                .richShare
                .toList
                .map(_.toJson)
            )
          )
        )
      }

  def listShareObservables(caseId: String, observableId: String): Action[AnyContent] =
    entrypoint("list observable shares")
      .authRoTransaction(db) { implicit request => implicit graph =>
        Success(
          Results.Ok(
            JsArray(
              caseSrv
                .get(caseId)
                .can(Permissions.manageShare)
                .shares
                .filter(_.organisation.hasNot("name", request.organisation).visible)
                .byObservable(observableId)
                .richShare
                .toList
                .map(_.toJson)
            )
          )
        )
      }

  def shareTask(taskId: String): Action[AnyContent] =
    entrypoint("share task")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisationIds: Seq[String] = request.body("organisations")

        for {
          task          <- taskSrv.getOrFail(taskId)
          _             <- taskSrv.get(task).`case`.can(Permissions.manageShare).existsOrFail()
          organisations <- organisationIds.toTry(organisationSrv.get(_).visible.getOrFail())
          _             <- shareSrv.addTaskShares(task, organisations)
        } yield Results.NoContent
      }

  def shareObservable(observableId: String): Action[AnyContent] =
    entrypoint("share observable")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisationIds: Seq[String] = request.body("organisations")
        for {
          observable    <- observableSrv.getOrFail(observableId)
          _             <- observableSrv.get(observable).`case`.can(Permissions.manageShare).existsOrFail()
          organisations <- organisationIds.toTry(organisationSrv.get(_).visible.getOrFail())
          _             <- shareSrv.addObservableShares(observable, organisations)
        } yield Results.NoContent
      }

}
