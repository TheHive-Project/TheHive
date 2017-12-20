package global

import javax.inject.{ Inject, Provider, Singleton }

import play.api.Logger
import play.api.http.SessionConfiguration
import play.api.libs.crypto.CSRFTokenSigner
import play.api.mvc.RequestHeader
import play.filters.csrf.CSRF.{ ErrorHandler, TokenProvider }
import play.filters.csrf.CSRFConfig

import akka.stream.Materializer

object CSRFFilter {
  private[CSRFFilter] lazy val logger = Logger(getClass)

  def shouldProtect(request: RequestHeader): Boolean = {
    val isLogin = request.uri.startsWith("/api/login")
    val isApi = request.uri.startsWith("/api")
    val isInSession = request.session.data.nonEmpty
    val check = !isLogin && isApi && isInSession
    logger.debug(s"[csrf] uri ${request.uri} (isLogin=$isLogin, isApi=$isApi, isInSession=$isInSession): ${if (check) "" else "don't"} check")
    check
  }

}

@Singleton
class CSRFFilter @Inject() (
    config: Provider[CSRFConfig],
    tokenSignerProvider: Provider[CSRFTokenSigner],
    sessionConfiguration: SessionConfiguration,
    tokenProvider: TokenProvider,
    errorHandler: ErrorHandler)(mat: Materializer)
  extends play.filters.csrf.CSRFFilter(
    config.get.copy(shouldProtect = CSRFFilter.shouldProtect),
    tokenSignerProvider.get,
    sessionConfiguration,
    tokenProvider,
    errorHandler)(mat)