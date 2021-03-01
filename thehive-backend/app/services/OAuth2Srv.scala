package services

import java.util.UUID

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import org.elastic4play.services.{AuthContext, AuthSrv}
import org.elastic4play.{AuthenticationError, BadRequestError, NotFoundError}
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.{Configuration, Logger}
import services.mappers.UserMapper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class OAuth2Config(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    responseType: String,
    grantType: String,
    authorizationUrl: String,
    tokenUrl: String,
    userUrl: String,
    scope: Seq[String],
    authorizationHeader: String,
    autoupdate: Boolean,
    autocreate: Boolean
)

object OAuth2Config {

  def apply(configuration: Configuration): Option[OAuth2Config] =
    for {
      clientId     <- configuration.getOptional[String]("auth.oauth2.clientId")
      clientSecret <- configuration.getOptional[String]("auth.oauth2.clientSecret")
      redirectUri  <- configuration.getOptional[String]("auth.oauth2.redirectUri")
      responseType <- configuration.getOptional[String]("auth.oauth2.responseType")
      grantType = configuration.getOptional[String]("auth.oauth2.grantType").getOrElse("authorization_code")
      authorizationUrl <- configuration.getOptional[String]("auth.oauth2.authorizationUrl")
      tokenUrl         <- configuration.getOptional[String]("auth.oauth2.tokenUrl")
      userUrl          <- configuration.getOptional[String]("auth.oauth2.userUrl")
      scope            <- configuration.getOptional[Seq[String]]("auth.oauth2.scope")
      authorizationHeader = configuration.getOptional[String]("auth.oauth2.authorizationHeader").getOrElse("Bearer")
      autocreate          = configuration.getOptional[Boolean]("auth.sso.autocreate").getOrElse(false)
      autoupdate          = configuration.getOptional[Boolean]("auth.sso.autoupdate").getOrElse(false)
    } yield OAuth2Config(
      clientId,
      clientSecret,
      redirectUri,
      responseType,
      grantType,
      authorizationUrl,
      tokenUrl,
      userUrl,
      scope,
      authorizationHeader,
      autocreate,
      autoupdate
    )
}

@Singleton
class OAuth2Srv(
    ws: WSClient,
    userSrv: UserSrv,
    ssoMapper: UserMapper,
    oauth2Config: Option[OAuth2Config],
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer
) extends AuthSrv {

  @Inject() def this(ws: WSClient, ssoMapper: UserMapper, userSrv: UserSrv, configuration: Configuration, ec: ExecutionContext, mat: Materializer) =
    this(ws, userSrv, ssoMapper, OAuth2Config(configuration), ec, mat)

  override val name: String = "oauth2"
  private val logger        = Logger(getClass)

  val Oauth2TokenQueryString = "code"

  private def withOAuth2Config[A](body: OAuth2Config => Future[A]): Future[A] =
    oauth2Config.fold[Future[A]](Future.failed(AuthenticationError("OAuth2 not configured properly")))(body)

  override def authenticate()(implicit request: RequestHeader): Future[Either[Result, AuthContext]] =
    withOAuth2Config { oauth2Config =>
      if (!isSecuredAuthCode(request)) {
        logger.debug("Code or state is not provided, redirect to authorizationUrl")
        Future.successful(Left(authRedirect(oauth2Config)))
      } else {
        (for {
          token       <- getToken(oauth2Config, request)
          userData    <- getUserData(oauth2Config, token)
          authContext <- authenticate(oauth2Config, request, userData)
        } yield Right(authContext)).recoverWith {
          case error => Future.failed(AuthenticationError(s"OAuth2 authentication failure: ${error.getMessage}"))
        }
      }
    }

  private def isSecuredAuthCode(request: RequestHeader): Boolean =
    request.queryString.contains("code") && request.queryString.contains("state")

  /**
    * Filter checking whether we initiate the OAuth2 process
    * and redirecting to OAuth2 server if necessary
    * @return
    */
  private def authRedirect(oauth2Config: OAuth2Config): Result = {
    val state = UUID.randomUUID().toString
    val queryStringParams = Map[String, Seq[String]](
      "scope"         -> Seq(oauth2Config.scope.mkString(" ")),
      "response_type" -> Seq(oauth2Config.responseType),
      "redirect_uri"  -> Seq(oauth2Config.redirectUri),
      "client_id"     -> Seq(oauth2Config.clientId),
      "state"         -> Seq(state)
    )

    logger.debug(s"Redirecting to ${oauth2Config.redirectUri} with $queryStringParams and state $state")
    Results
      .Redirect(oauth2Config.authorizationUrl, queryStringParams, status = 302)
      .withSession("state" -> state)
  }

  /**
    * Enriching the initial request with OAuth2 token gotten
    * from OAuth2 code
    * @return
    */
  private def getToken[A](oauth2Config: OAuth2Config, request: RequestHeader): Future[String] = {
    val token =
      for {
        state   <- request.session.get("state")
        stateQs <- request.queryString.get("state").flatMap(_.headOption)
        if state == stateQs
      } yield request.queryString.get("code").flatMap(_.headOption) match {
        case Some(code) =>
          logger.debug(s"Attempting to retrieve OAuth2 token from ${oauth2Config.tokenUrl} with code $code")
          getAuthTokenFromCode(oauth2Config, code, state)
            .map { t =>
              logger.trace(s"Got token $t")
              t
            }
        case None =>
          Future.failed(AuthenticationError(s"OAuth2 server code missing ${request.queryString.get("error")}"))
      }
    token.getOrElse(Future.failed(BadRequestError("OAuth2 states mismatch")))
  }

  /**
    * Querying the OAuth2 server for a token
    * @param code the previously obtained code
    * @return
    */
  private def getAuthTokenFromCode(oauth2Config: OAuth2Config, code: String, state: String): Future[String] = {
    logger.trace(s"""
                    |Request to ${oauth2Config.tokenUrl} with
                    |  code:          $code
                    |  grant_type:    ${oauth2Config.grantType}
                    |  client_secret: ${oauth2Config.clientSecret}
                    |  redirect_uri:  ${oauth2Config.redirectUri}
                    |  client_id:     ${oauth2Config.clientId}
                    |  state:         $state
                    |""".stripMargin)
    ws.url(oauth2Config.tokenUrl)
      .withHttpHeaders("Accept" -> "application/json")
      .post(
        Map(
          "code"          -> code,
          "grant_type"    -> oauth2Config.grantType,
          "client_secret" -> oauth2Config.clientSecret,
          "redirect_uri"  -> oauth2Config.redirectUri,
          "client_id"     -> oauth2Config.clientId,
          "state"         -> state
        )
      )
      .transform {
        case Success(r) if r.status == 200 => Success((r.json \ "access_token").asOpt[String].getOrElse(""))
        case Failure(error)                => Failure(AuthenticationError(s"OAuth2 token verification failure ${error.getMessage}"))
        case Success(r)                    => Failure(AuthenticationError(s"OAuth2/token unexpected response from server (${r.status} ${r.statusText})"))
      }
  }

  /**
    * Client query for user data with OAuth2 token
    * @param token the token
    * @return
    */
  private def getUserData(oauth2Config: OAuth2Config, token: String): Future[JsObject] = {
    logger.trace(s"Request to ${oauth2Config.userUrl} with authorization header: ${oauth2Config.authorizationHeader} $token")
    ws.url(oauth2Config.userUrl)
      .addHttpHeaders("Authorization" -> s"${oauth2Config.authorizationHeader} $token")
      .get()
      .transform {
        case Success(r) if r.status == 200 => Success(r.json.as[JsObject])
        case Failure(error)                => Failure(AuthenticationError(s"OAuth2 user data fetch failure ${error.getMessage}"))
        case Success(r)                    => Failure(AuthenticationError(s"OAuth2/userinfo unexpected response from server (${r.status} ${r.statusText})"))
      }
  }

  private def authenticate(oauth2Config: OAuth2Config, request: RequestHeader, userData: JsObject): Future[AuthContext] =
    for {
      userFields <- ssoMapper.getUserFields(userData)
      login      <- userFields.getString("login").fold(Future.failed[String](AuthenticationError("")))(Future.successful)
      user <- userSrv
        .get(login)
        .flatMap {
          case u if oauth2Config.autoupdate =>
            logger.debug(s"Updating OAuth/OIDC user")
            userSrv.inInitAuthContext { implicit authContext =>
              // Only update name and roles, not login (can't change it)
              userSrv
                .update(u, userFields.unset("login"))

            }
          case u => Future.successful(u)
        }
        .recoverWith {
          case _: NotFoundError if oauth2Config.autocreate =>
            logger.debug(s"Creating OAuth/OIDC user")
            userSrv.inInitAuthContext { implicit authContext =>
              userSrv.create(userFields.set("login", userFields.getString("login").get.toLowerCase))
            }
        }
      authContext <- userSrv.getFromUser(request, user, name)
    } yield authContext
}
