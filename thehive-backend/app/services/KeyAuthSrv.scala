package services

import java.util.Base64
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

import play.api.libs.json.JsArray
import play.api.mvc.RequestHeader

import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import org.elastic4play.controllers.Fields
import org.elastic4play.services.{AuthCapability, AuthContext, AuthSrv}
import org.elastic4play.{AuthenticationError, BadRequestError}

@Singleton
class KeyAuthSrv @Inject()(userSrv: UserSrv, implicit val ec: ExecutionContext, implicit val mat: Materializer) extends AuthSrv {
  override val name = "key"

  final protected def generateKey(): String = {
    val bytes = Array.ofDim[Byte](24)
    Random.nextBytes(bytes)
    Base64.getEncoder.encodeToString(bytes)
  }

  override val capabilities = Set(AuthCapability.authByKey)

  override def authenticate(key: String)(implicit request: RequestHeader): Future[AuthContext] = {
    import org.elastic4play.services.QueryDSL._
    // key attribute is sensitive so it is not possible to search on that field
    userSrv
      .find("status" ~= "Ok", Some("all"), Nil)
      ._1
      .filter(_.key().contains(key))
      .runWith(Sink.headOption)
      .flatMap {
        case Some(user) => userSrv.getFromUser(request, user, name)
        case None       => Future.failed(AuthenticationError("Authentication failure"))
      }
  }

  override def renewKey(username: String)(implicit authContext: AuthContext): Future[String] = {
    val newKey = generateKey()
    userSrv.update(username, Fields.empty.set("key", newKey)).map(_ => newKey)
  }

  override def getKey(username: String)(implicit authContext: AuthContext): Future[String] =
    userSrv.get(username).map(_.key().getOrElse(throw BadRequestError(s"User $username hasn't key")))

  override def removeKey(username: String)(implicit authContext: AuthContext): Future[Unit] =
    userSrv.update(username, Fields.empty.set("key", JsArray())).map(_ => ())
}
