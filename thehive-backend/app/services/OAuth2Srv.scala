package services

import java.util.UUID

import javax.inject.{ Inject, Singleton }
import akka.stream.Materializer

import org.elastic4play.services.{ AuthContext, AuthSrv }
import org.elastic4play.{ AuthenticationError, AuthorizationError, BadRequestError, CreateError, NotFoundError, OAuth2Redirect }
import play.api.http.Status
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.ws.WSClient
import play.api.mvc.{ Request, RequestHeader, Result, Results }
import play.api.{ Configuration, Logger }

import services.mappers.UserMapper
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

import org.elastic4play.controllers.AuthenticatedRequest


case class OAuth2Config(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    responseType: String,
    grantType: String,
    authorizationUrl: String,
    tokenUrl: String,
    userUrl: String,
    scope: String,
    authorizationHeader: String,
    userIdField: String,
      autoupdate: Boolean,
autocreate: Boolean
)

object OAuth2Config {

  def apply(configuration: Configuration): Option[OAuth2Config] =
    for {
      clientId         ← configuration.getOptional[String]("auth.oauth2.clientId")
      clientSecret     ← configuration.getOptional[String]("auth.oauth2.clientSecret")
      redirectUri      ← configuration.getOptional[String]("auth.oauth2.redirectUri")
      responseType     ← configuration.getOptional[String]("auth.oauth2.responseType")
      grantType        ← configuration.getOptional[String]("auth.oauth2.grantType")
      authorizationUrl ← configuration.getOptional[String]("auth.oauth2.authorizationUrl")
      userUrl          ← configuration.getOptional[String]("auth.oauth2.userUrl")
      tokenUrl         ← configuration.getOptional[String]("auth.oauth2.tokenUrl")
      scope            ← configuration.getOptional[String]("auth.oauth2.scope")
      authorizationHeader = configuration.getOptional[String]("auth.oauth2.authorizationHeader").getOrElse("Bearer")
      autocreate = configuration.getOptional[Boolean]("auth.sso.autocreate").getOrElse(false)
      autoupdate = configuration.getOptional[Boolean]("auth.sso.autoupdate").getOrElse(false)
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

  private def withOAuth2Config[A](body: OAuth2Config ⇒ Future[A]): Future[A] =
    oauth2Config.fold[Future[A]](Future.failed(AuthenticationError("OAuth2 not configured properly")))(body)

  override def authenticate()(implicit request: RequestHeader): Future[AuthContext] =
    withOAuth2Config { oauth2Config ⇒
      val ssoResponse = oauth2Config.grantType match {
        case "authorization_code" =>
          if (!isSecuredAuthCode(request)) {
            logger.debug("Code or state is not provided, redirect to authorizationUrl")
            Future.successful(authRedirect(oauth2Config))
          } else {
            for {
              token    <- getToken(oauth2Config, request)
              userData <- getUserData(oauth2Config, token)
              authReq <- Future
                .fromTry(authenticate(request, userData))
                .recoverWith {
                  case _: NotFoundError => Future.fromTry(createUser(request, userData))
                }
                .recoverWith {
                  case _: CreateError => Future.failed(NotFoundError("User not found"))
                }
            } yield sessionAuthSrv.setSessionUser(authReq.authContext)(Results.Found(httpContext))
          }

        case x => Future.failed(BadConfigurationError(s"OAuth GrantType $x not supported yet"))
      }
      ssoResponse.recoverWith {
        case error => Future.successful(Results.Redirect(httpContext, Map("error" -> Seq(error.getMessage))))
      }





      request
        .queryString
        .get(Oauth2TokenQueryString)
        .flatMap(_.headOption)
        .fold(createOauth2Redirect(oauth2Config.clientId)) { code ⇒
          getAuthTokenAndAuthenticate(oauth2Config.clientId, code)
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
      "response_type" -> Seq("code"),
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
    ws
      .url(oauth2Config.tokenUrl)
      .withHttpHeaders("Accept" -> "application/json")
      .post(
        Map(
          "code"          -> code,
          "grant_type"    -> oauth2Config.grantType.toString,
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
    ws
      .url(oauth2Config.userUrl)
      .addHttpHeaders("Authorization" -> s"${oauth2Config.authorizationHeader} $token")
      .get()
      .transform {
        case Success(r) if r.status == 200 => Success(r.json.as[JsObject])
        case Failure(error)                => Failure(AuthenticationError(s"OAuth2 user data fetch failure ${error.getMessage}"))
        case Success(r)                    => Failure(AuthenticationError(s"OAuth2/userinfo unexpected response from server (${r.status} ${r.statusText})"))
      }
  }



  private def authenticate[A](oauth2Config: OAuth2Config, request: Request[A], userData: JsObject): Future[AuthenticatedRequest[A]] =
    for {
      userId      <- getUserId(oauth2Config, userData)
      user <- userSrv.get(userId)
      authContext <- userSrv.getFromUser(request, user, "oauth2")
    } yield new AuthenticatedRequest[A](authContext, request)

  private def getUserId(oauth2Config: OAuth2Config, jsonUser: JsObject): Try[String] =
    (jsonUser \ oauth2Config.userIdField).asOpt[String] match {
      case Some(userId) => Success(userId)
      case None         => Failure(BadRequestError(s"OAuth2 user data doesn't contain user ID field (${oauth2Config.userIdField})"))
    }


//
//
//
//
//  private def getAuthTokenAndAuthenticate(clientId: String, code: String)(implicit request: RequestHeader): Future[AuthContext] = {
//    logger.debug("Getting user token with the code from the response")
//    withOAuth2Config { cfg ⇒
//      ws.url(cfg.tokenUrl)
//        .post(
//          Map(
//            "code"          → code,
//            "grant_type"    → cfg.grantType,
//            "client_secret" → cfg.clientSecret,
//            "redirect_uri"  → cfg.redirectUri,
//            "client_id"     → clientId
//          )
//        )
//        .recoverWith {
//          case error ⇒
//            logger.error(s"Token verification failure", error)
//            Future.failed(AuthenticationError("Token verification failure"))
//        }
//        .flatMap { r ⇒
//          r.status match {
//            case Status.OK ⇒
//              logger.debug("Getting user info using access token")
//              val accessToken = (r.json \ "access_token").asOpt[String].getOrElse("")
//              val authHeader  = "Authorization" → s"Bearer $accessToken"
//              ws.url(cfg.userUrl)
//                .addHttpHeaders(authHeader)
//                .get()
//                .flatMap { userResponse ⇒
//                  if (userResponse.status != Status.OK) {
//                    Future.failed(AuthenticationError(s"Unexpected response from server: ${userResponse.status} ${userResponse.body}"))
//                  } else {
//                    val response = userResponse.json.asInstanceOf[JsObject]
//                    getOrCreateUser(response, authHeader)
//                  }
//                }
//            case _ ⇒
//              logger.error(s"Unexpected response from server: ${r.status} ${r.body}")
//              Future.failed(AuthenticationError("Unexpected response from server"))
//          }
//        }
//    }
//  }
//
//  private def getOrCreateUser(response: JsValue, authHeader: (String, String))(implicit request: RequestHeader): Future[AuthContext] =
//    withOAuth2Config { cfg ⇒
//      ssoMapper.getUserFields(response, Some(authHeader)).flatMap { userFields ⇒
//        val userId = userFields.getString("login").getOrElse("")
//        userSrv
//          .get(userId)
//          .flatMap(user ⇒ {
//            if (cfg.autoupdate) {
//              logger.debug(s"Updating OAuth/OIDC user")
//              userSrv.inInitAuthContext { implicit authContext ⇒
//                // Only update name and roles, not login (can't change it)
//                userSrv
//                  .update(user, userFields.unset("login"))
//                  .flatMap(user ⇒ {
//                    userSrv.getFromUser(request, user, name)
//                  })
//              }
//            } else {
//              userSrv.getFromUser(request, user, name)
//            }
//          })
//          .recoverWith {
//            case authErr: AuthorizationError ⇒ Future.failed(authErr)
//            case _ if cfg.autocreate ⇒
//              logger.debug(s"Creating OAuth/OIDC user")
//              userSrv.inInitAuthContext { implicit authContext ⇒
//                userSrv
//                  .create(userFields)
//                  .flatMap(user ⇒ {
//                    userSrv.getFromUser(request, user, name)
//                  })
//              }
//          }
//      }
//    }
//
//  private def createOauth2Redirect(clientId: String): Future[AuthContext] =
//    withOAuth2Config { cfg ⇒
//      val queryStringParams = Map[String, Seq[String]](
//        "scope"         → Seq(cfg.scope),
//        "response_type" → Seq(cfg.responseType),
//        "redirect_uri"  → Seq(cfg.redirectUri),
//        "client_id"     → Seq(clientId)
//      )
//      Future.failed(OAuth2Redirect(cfg.authorizationUrl, queryStringParams))
//    }
}
