package services

import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import play.api.mvc.RequestHeader

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.{ User, UserModel, UserStatus }

import org.elastic4play.controllers.Fields
import org.elastic4play.database.DBIndex
import org.elastic4play.services._
import org.elastic4play.utils.Instance
import org.elastic4play.{ AuthenticationError, AuthorizationError }

@Singleton
class UserSrv @Inject() (
    userModel: UserModel,
    createSrv: CreateSrv,
    getSrv: GetSrv,
    updateSrv: UpdateSrv,
    deleteSrv: DeleteSrv,
    findSrv: FindSrv,
    eventSrv: EventSrv,
    authSrv: Provider[AuthSrv],
    dbIndex: DBIndex,
    implicit val ec: ExecutionContext) extends org.elastic4play.services.UserSrv {

  private case class AuthContextImpl(userId: String, userName: String, requestId: String, roles: Seq[Role.Type]) extends AuthContext

  override def getFromId(request: RequestHeader, userId: String): Future[AuthContext] = {
    getSrv[UserModel, User](userModel, userId)
      .flatMap { user ⇒ getFromUser(request, user) }
  }

  override def getFromUser(request: RequestHeader, user: org.elastic4play.services.User): Future[AuthContext] = {
    user match {
      case u: User if u.status() == UserStatus.Ok ⇒ Future.successful(AuthContextImpl(user.id, user.getUserName, Instance.getRequestId(request), user.getRoles))
      case _                                      ⇒ Future.failed(AuthorizationError("Your account is locked"))
    }

  }

  override def getInitialUser(request: RequestHeader): Future[AuthContext] =
    dbIndex.getSize(userModel.name).map {
      case size if size > 0 ⇒ throw AuthenticationError(s"Use of initial user is forbidden because users exist in database")
      case _                ⇒ AuthContextImpl("init", "", Instance.getRequestId(request), Seq(Role.admin, Role.read))
    }

  override def inInitAuthContext[A](block: AuthContext ⇒ Future[A]): Future[A] = {
    val authContext = AuthContextImpl("init", "", Instance.getInternalId, Seq(Role.admin, Role.read))
    eventSrv.publish(StreamActor.Initialize(authContext.requestId))
    block(authContext).andThen {
      case _ ⇒ eventSrv.publish(StreamActor.Commit(authContext.requestId))
    }
  }

  def create(fields: Fields)(implicit authContext: AuthContext): Future[User] = {
    fields.getString("password") match {
      case None ⇒ createSrv[UserModel, User](userModel, fields)
      case Some(password) ⇒ createSrv[UserModel, User](userModel, fields.unset("password")).flatMap { user ⇒
        authSrv.get.setPassword(user.userId(), password).map(_ ⇒ user)
      }
    }
  }

  override def get(id: String): Future[User] = getSrv[UserModel, User](userModel, id)

  def update(id: String, fields: Fields)(implicit Context: AuthContext): Future[User] = {
    updateSrv[UserModel, User](userModel, id, fields)
  }

  def delete(id: String)(implicit Context: AuthContext): Future[User] =
    deleteSrv[UserModel, User](userModel, id)

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[User, NotUsed], Future[Long]) = {
    findSrv[UserModel, User](userModel, queryDef, range, sortBy)
  }
}
