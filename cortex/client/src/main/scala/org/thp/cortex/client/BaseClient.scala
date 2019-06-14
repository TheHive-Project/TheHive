package org.thp.cortex.client

import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.WSResponse

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class ApplicationError(status: Int, body: JsValue) extends Exception(s"ApplicationError($status):\n${Json.prettyPrint(body)}")

object ApplicationError {
  def apply(r: WSResponse): ApplicationError = ApplicationError(r.status, Try(r.body[JsValue]).getOrElse(Json.obj("body" → r.body)))
}

object Client {
  lazy val logger = Logger(getClass)
}

class BaseClient[Input: Writes, Output: Reads](baseUrl: String)(implicit ws: CustomWSAPI) {

  def create(input: Input)(implicit ec: ExecutionContext, auth: Authentication): Future[Output] = {
    val body = Json.toJson(input)
    Client.logger.debug(s"Request POST $baseUrl\n${Json.prettyPrint(body)}")
    auth(ws.url(baseUrl))
      .post(body)
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue].as[Output])
        case Success(r)                               ⇒ Failure(ApplicationError(r))
        case Failure(t)                               ⇒ throw t
      }
  }

  def get(id: String)(implicit ec: ExecutionContext, auth: Authentication): Future[Output] = {
    Client.logger.debug(s"Request GET $baseUrl/$id")
    auth(ws.url(s"$baseUrl/$id"))
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue].as[Output])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }
  }

  def list(implicit ec: ExecutionContext, auth: Authentication): Future[Seq[Output]] = {
    Client.logger.debug(s"Request GET $baseUrl")
    auth(ws.url(baseUrl))
      .get
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue].as[Seq[Output]])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }
  }
}
