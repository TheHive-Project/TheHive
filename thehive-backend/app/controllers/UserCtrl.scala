package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc._

import services.UserSrv

import org.elastic4play.controllers.{ Authenticated, Fields, FieldsBodyParser, Renderer }
import org.elastic4play.models.JsonFormat.baseModelEntityWrites
import org.elastic4play.services.JsonFormat.queryReads
import org.elastic4play.services.{ AuthSrv, QueryDSL, QueryDef, Role }
import org.elastic4play.{ AuthorizationError, MissingAttributeError, Timed }

@Singleton
class UserCtrl @Inject() (
    userSrv: UserSrv,
    authSrv: AuthSrv,
    authenticated: Authenticated,
    renderer: Renderer,
    fieldsBodyParser: FieldsBodyParser,
    components: ControllerComponents,
    implicit val ec: ExecutionContext) extends AbstractController(components) with Status {

  private[UserCtrl] lazy val logger = Logger(getClass)

  @Timed
  def create: Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    userSrv.create(request.body)
      .map(user ⇒ renderer.toOutput(CREATED, user))
  }

  @Timed
  def get(id: String): Action[AnyContent] = authenticated(Role.read).async { implicit request ⇒
    userSrv.get(id)
      .map { user ⇒
        val json = if (request.roles.contains(Role.admin)) user.toAdminJson else user.toJson
        renderer.toOutput(OK, json)
      }
  }

  @Timed
  def update(id: String): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    if (id == request.authContext.userId || request.authContext.roles.contains(Role.admin)) {
      if (request.body.contains("password"))
        logger.warn("Change password attribute using update operation is deprecated. Please use dedicated API (setPassword and changePassword)")
      userSrv.update(id, request.body.unset("password")).map { user ⇒
        renderer.toOutput(OK, user)
      }
    }
    else {
      Future.failed(AuthorizationError("You are not permitted to change user settings"))
    }
  }

  @Timed
  def setPassword(login: String): Action[Fields] = authenticated(Role.admin).async(fieldsBodyParser) { implicit request ⇒
    request.body.getString("password")
      .fold(Future.failed[Result](MissingAttributeError("password"))) { password ⇒
        authSrv.setPassword(login, password).map(_ ⇒ NoContent)
      }
  }

  @Timed
  def changePassword(login: String): Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    if (login == request.authContext.userId) {
      val fields = request.body
      fields.getString("password").fold(Future.failed[Result](MissingAttributeError("password"))) { password ⇒
        fields.getString("currentPassword").fold(Future.failed[Result](MissingAttributeError("currentPassword"))) { currentPassword ⇒
          authSrv.changePassword(request.authContext.userId, currentPassword, password)
            .map(_ ⇒ NoContent)
        }
      }
    }
    else
      Future.failed(AuthorizationError("You can't change password of another user"))
  }

  @Timed
  def delete(id: String): Action[AnyContent] = authenticated(Role.admin).async { implicit request ⇒
    userSrv.delete(id)
      .map(_ ⇒ NoContent)
  }

  @Timed
  def currentUser: Action[AnyContent] = Action.async { implicit request ⇒
    for {
      authContext ← authenticated.getContext(request)
      user ← userSrv.get(authContext.userId)
      preferences = Try(Json.parse(user.preferences()))
        .recover {
          case _ ⇒
            logger.warn(s"User ${authContext.userId} has invalid preference format: ${user.preferences()}")
            JsObject(Nil)
        }
        .get
      json = user.toJson + ("preferences" → preferences)
    } yield renderer.toOutput(OK, json)
  }

  @Timed
  def find: Action[Fields] = authenticated(Role.read).async(fieldsBodyParser) { implicit request ⇒
    val query = request.body.getValue("query").fold[QueryDef](QueryDSL.any)(_.as[QueryDef])
    val range = request.body.getString("range")
    val sort = request.body.getStrings("sort").getOrElse(Nil)
    val (users, total) = userSrv.find(query, range, sort)
    renderer.toOutput(OK, users, total)
  }
}