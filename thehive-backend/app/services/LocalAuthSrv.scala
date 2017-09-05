package services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

import play.api.mvc.RequestHeader

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import models.User

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ AuthCapability, AuthContext, AuthSrv }
import org.elastic4play.utils.Hasher
import org.elastic4play.{ AuthenticationError, AuthorizationError, BadRequestError }

@Singleton
class LocalAuthSrv @Inject() (
    userSrv: UserSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) extends AuthSrv {

  val name = "local"
  override val capabilities = Set(AuthCapability.changePassword, AuthCapability.setPassword, AuthCapability.renewKey)

  private[services] def doAuthenticate(user: User, password: String): Boolean = {
    user.password().map(_.split(",", 2)).fold(false) {
      case Array(seed, pwd) ⇒
        val hash = Hasher("SHA-256").fromString(seed + password).head.toString
        hash == pwd
      case _ ⇒ false
    }
  }

  override def authenticate(username: String, password: String)(implicit request: RequestHeader): Future[AuthContext] = {
    userSrv.get(username).flatMap { user ⇒
      if (doAuthenticate(user, password)) userSrv.getFromUser(request, user)
      else Future.failed(AuthenticationError("Authentication failure"))
    }
  }

  override def authenticate(key: String)(implicit request: RequestHeader): Future[AuthContext] = {
    import org.elastic4play.services.QueryDSL._
    // key attribute is sensitive so it is not possible to search on that field
    userSrv.find("status" ~= "Ok", Some("all"), Nil)
      ._1
      .filter(_.key().contains(key))
      .runWith(Sink.headOption)
      .flatMap {
        case Some(user) ⇒ userSrv.getFromUser(request, user)
        case None       ⇒ Future.failed(AuthenticationError("Authentication failure"))
      }
  }

  override def changePassword(username: String, oldPassword: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] = {
    userSrv.get(username).flatMap { user ⇒
      if (doAuthenticate(user, oldPassword)) setPassword(username, newPassword)
      else Future.failed(AuthorizationError("Authentication failure"))
    }
  }

  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Future[Unit] = {
    val seed = Random.nextString(10).replace(',', '!')
    val newHash = seed + "," + Hasher("SHA-256").fromString(seed + newPassword).head.toString
    userSrv.update(username, Fields.empty.set("password", newHash)).map(_ ⇒ ())
  }

  override def renewKey(username: String)(implicit authContext: AuthContext): Future[String] = {
    val newKey = generateKey()
    userSrv.update(username, Fields.empty.set("key", newKey)).map(_ ⇒ newKey)
  }

  override def getKey(username: String)(implicit authContext: AuthContext): Future[String] = {
    userSrv.get(username).map(_.key().getOrElse(throw BadRequestError(s"User $username hasn't key")))
  }

}