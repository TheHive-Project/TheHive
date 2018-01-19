package services

import javax.inject.{ Inject, Singleton }

import akka.stream.Materializer
import org.elastic4play.services.{ AuthContext, AuthSrv }
import org.elastic4play.{ AuthenticationError, AuthorizationError, OAuth2Redirect }
import play.api.http.Status
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.ws.WSClient
import play.api.mvc.RequestHeader
import play.api.{ Configuration, Logger }
import services.mappers.UserMapper

import scala.concurrent.{ ExecutionContext, Future }

case class OAuth2Config(
    clientId: Option[String] = None,
    clientSecret: String,
    redirectUri: String,
    responseType: String,
    grantType: String,
    authorizationUrl: String,
    tokenUrl: String,
    userUrl: String,
    scope: String,
    autocreate: Boolean)

object OAuth2Config {
  def apply(configuration: Configuration): OAuth2Config = {
    (for {
      clientId ← configuration.getOptional[String]("auth.oauth2.clientId")
      clientSecret ← configuration.getOptional[String]("auth.oauth2.clientSecret")
      redirectUri ← configuration.getOptional[String]("auth.oauth2.redirectUri")
      responseType ← configuration.getOptional[String]("auth.oauth2.responseType")
      grantType ← configuration.getOptional[String]("auth.oauth2.grantType")
      authorizationUrl ← configuration.getOptional[String]("auth.oauth2.authorizationUrl")
      userUrl ← configuration.getOptional[String]("auth.oauth2.userUrl")
      tokenUrl ← configuration.getOptional[String]("auth.oauth2.tokenUrl")
      scope ← configuration.getOptional[String]("auth.oauth2.scope")
      autocreate ← configuration.getOptional[Boolean]("auth.sso.autocreate").orElse(Some(false))
    } yield OAuth2Config(Some(clientId), clientSecret, redirectUri, responseType, grantType, authorizationUrl, tokenUrl, userUrl, scope, autocreate))
      .getOrElse(OAuth2Config(tokenUrl = "", clientSecret = "", redirectUri = "", responseType = "", grantType = "", authorizationUrl = "", userUrl = "", scope = "", autocreate = false))
  }
}

@Singleton
class OAuth2Srv(
    ws: WSClient,
    userSrv: UserSrv,
    ssoMapper: UserMapper,
    oauth2Config: OAuth2Config,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer)
  extends AuthSrv {

  @Inject() def this(
      ws: WSClient,
      ssoMapper: UserMapper,
      userSrv: UserSrv,
      configuration: Configuration,
      ec: ExecutionContext,
      mat: Materializer) = this(
    ws,
    userSrv,
    ssoMapper,
    OAuth2Config(configuration),
    ec,
    mat)

  override val name: String = "oauth2"
  private val logger = Logger(getClass)

  val Oauth2TokenQueryString = "code"

  override def authenticate()(implicit request: RequestHeader): Future[AuthContext] = {
    oauth2Config.clientId
      .fold[Future[AuthContext]](Future.failed(AuthenticationError("OAuth2 not configured properly"))) {
        clientId ⇒
          request.queryString
            .get(Oauth2TokenQueryString)
            .flatMap(_.headOption)
            .fold(createOauth2Redirect(clientId)) { code ⇒
              getAuthTokenAndAuthenticate(clientId, code)
            }
      }
  }

  private def getAuthTokenAndAuthenticate(clientId: String, code: String)(implicit request: RequestHeader): Future[AuthContext] = {
    logger.debug("Getting user token with the code from the response!")
    ws.url(oauth2Config.tokenUrl)
      .post(Map(
        "code" -> code,
        "grant_type" -> oauth2Config.grantType,
        "client_secret" -> oauth2Config.clientSecret,
        "redirect_uri" -> oauth2Config.redirectUri,
        "client_id" -> clientId))
      .recoverWith {
        case error ⇒
          logger.error(s"Token verification failure", error)
          Future.failed(AuthenticationError("Token verification failure"))
      }
      .flatMap { r ⇒
        r.status match {
          case Status.OK ⇒
            val accessToken = (r.json \ "access_token").asOpt[String].getOrElse("")
            val authHeader = "Authorization" -> s"bearer $accessToken"
            ws.url(oauth2Config.userUrl)
              .addHttpHeaders(authHeader)
              .get().flatMap { userResponse ⇒
                if (userResponse.status != Status.OK) {
                  Future.failed(AuthenticationError(s"unexpected response from server: ${userResponse.status} ${userResponse.body}"))
                }
                else {
                  val response = userResponse.json.asInstanceOf[JsObject]
                  getOrCreateUser(response, authHeader)
                }
              }
          case _ ⇒
            logger.error(s"unexpected response from server: ${r.status} ${r.body}")
            Future.failed(AuthenticationError("unexpected response from server"))
        }
      }
  }

  private def getOrCreateUser(response: JsValue, authHeader: (String, String))(implicit request: RequestHeader): Future[AuthContext] = {
    ssoMapper.getUserFields(response, Some(authHeader)).flatMap {
      userFields ⇒
        val userId = userFields.getString("login").getOrElse("")
        userSrv.get(userId).flatMap(user ⇒ {
          userSrv.getFromUser(request, user)
        }).recoverWith {
          case authErr: AuthorizationError ⇒ Future.failed(authErr)
          case _ if oauth2Config.autocreate ⇒
            userSrv.inInitAuthContext { implicit authContext ⇒
              userSrv.create(userFields).flatMap(user ⇒ {
                userSrv.getFromUser(request, user)
              })
            }
        }
    }
  }

  private def createOauth2Redirect(clientId: String): Future[AuthContext] = {
    val queryStringParams = Map[String, Seq[String]](
      "scope" -> Seq(oauth2Config.scope),
      "response_type" -> Seq(oauth2Config.responseType),
      "redirect_uri" -> Seq(oauth2Config.redirectUri),
      "client_id" -> Seq(clientId))
    Future.failed(OAuth2Redirect(oauth2Config.authorizationUrl, queryStringParams))
  }
}

