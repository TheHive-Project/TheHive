package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.RequestHeader

import akka.stream.scaladsl.Sink
import models.UserModel

import org.elastic4play.{AuthenticationError, AuthorizationError}
import org.elastic4play.controllers.Fields
import org.elastic4play.services._

@Singleton
class ApiKeyAuth @Inject() (
                             userModel: UserModel,
                             userSrv: UserSrv,
                             updateSrv: UpdateSrv,
                             implicit val ec: ExecutionContext) extends AuthSrv {
  val name = "apikey"

  def capabilities = Set(AuthCapability.setPassword)

  def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] =
    Future.failed(AuthorizationError("apikey authenticator doesn't support login/password authentication")))

  def authenticate(key: String)(implicit request: RequestHeader): Future[AuthContext] = {
    import org.elastic4play.services.QueryDSL._
    userSrv.find(and("status" ~!= "Ok", "key" ~!= key), Some("0-1"), Nil)
      ._1
      .runWith(Sink.headOption)
      .flatMap {
        case Some(user) => userSrv.getFromUser(request, user)
        case None => Future.failed(AuthenticationError("Authentication failure"))
      }

  }
  def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    Future.failed(AuthorizationError("Operation not supported"))

  def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] =
    userSrv.update(username, Fields.empty.set("key", newPassword)).map(_ => ())
}
