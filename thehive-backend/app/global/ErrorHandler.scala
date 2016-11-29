package global

import scala.annotation.implicitNotFound
import scala.concurrent.Future

import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result, Results }

import org.elasticsearch.client.transport.NoNodeAvailableException
import org.elasticsearch.index.IndexNotFoundException
import org.elasticsearch.transport.RemoteTransportException

import org.elastic4play.{ AttributeCheckingError, AuthenticationError, AuthorizationError, BadRequestError, InternalError }
import org.elastic4play.{ NotFoundError, SearchError, UpdateError }
import org.elastic4play.JsonFormat.attributeCheckingExceptionWrites

/**
 * This class handles errors. It traverses all causes of exception to find known error and shows the appropriate message
 */
class TheHiveErrorHandler extends HttpErrorHandler {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = Future.successful {
    Results.Status(statusCode)(s"A client error occurred (${request.uri} / ${request.path} : $message")
  }

  private def getRootCauseError(ex: Throwable): Option[Result] = {
    ex match {
      case AuthenticationError(message)    ⇒ Some(Results.Unauthorized(message))
      case AuthorizationError(message)     ⇒ Some(Results.Forbidden(message))
      case UpdateError(status, message, _) ⇒ Some(Results.InternalServerError(s"Update fails : $status $message" + message))
      case InternalError(message)          ⇒ Some(Results.InternalServerError(message))
      case nfe: NumberFormatException      ⇒ Some(Results.BadRequest("Invalid format " + nfe.getMessage))
      case NotFoundError(message)          ⇒ Some(Results.NotFound(message))
      case BadRequestError(message)        ⇒ Some(Results.BadRequest(message))
      case SearchError(message, _)         ⇒ Some(Results.BadRequest(message))
      case ace: AttributeCheckingError     ⇒ Some(Results.BadRequest(Json.toJson(ace)))
      case iae: IllegalArgumentException   ⇒ Some(Results.BadRequest(iae.getMessage))
      case nnae: NoNodeAvailableException  ⇒ Some(Results.InternalServerError("ElasticSearch cluster is unreachable"))
      case rte: RemoteTransportException ⇒
        rte.getCause match {
          case infe: IndexNotFoundException ⇒ Some(Results.Status(520))
          case t: Throwable                 ⇒ Some(Results.InternalServerError("Database error : " + t.getMessage))
        }
      case t: Throwable ⇒ Option(t.getCause).flatMap(getRootCauseError)
    }
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    val result = getRootCauseError(exception).getOrElse(Results.InternalServerError(exception.getClass.getName() + "/" + exception.getMessage))
    Logger.info(s"${request.method} ${request.uri} returned ${result.header.status}", exception)
    Future.successful(result)
  }
}
