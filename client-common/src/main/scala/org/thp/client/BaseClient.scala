package org.thp.client

import play.api.Logger
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class ApplicationError(status: Int, body: JsValue) extends Exception(s"ApplicationError($status):\n${Json.prettyPrint(body)}")

object ApplicationError {
  def apply(r: WSResponse): ApplicationError = ApplicationError(r.status, Try(r.body[JsValue]).getOrElse(Json.obj("body" -> r.body)))
}

class BaseClient[Input: Writes, Output: Reads](baseUrl: String)(implicit ws: WSClient) {
  lazy val logger = Logger(getClass)

  def create(input: Input, url: String = baseUrl)(implicit ec: ExecutionContext, auth: Authentication): Future[Output] = {
    val body = Json.toJson(input)
//    val url  = urlOverride.getOrElse(baseUrl)
    logger.debug(s"Request POST $url\n${Json.prettyPrint(body)}")
    auth(ws.url(url))
      .post(body)
      .transform {
        case Success(r) if r.status == Status.CREATED => Success(r.body[JsValue].as[Output])
        case Success(r)                               => Failure(ApplicationError(r))
        case Failure(t)                               => throw t
      }
  }

  def search[SearchInput: Writes](input: SearchInput)(implicit ec: ExecutionContext, auth: Authentication): Future[Seq[Output]] = {
    val body = Json.toJson(input)
    val url  = s"$baseUrl/_search"
    logger.debug(s"Request POST $url\n${Json.prettyPrint(body)}")
    auth(ws.url(url))
      .post(body)
      .transform {
        case Success(r) if r.status == Status.OK => Success(r.body[JsValue].as[Seq[Output]])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }
  }

  def get(id: String, urlFragments: String = "")(implicit ec: ExecutionContext, auth: Authentication): Future[Output] = {
    logger.debug(s"Request GET $baseUrl/$id$urlFragments")
    auth(ws.url(s"$baseUrl/$id$urlFragments"))
      .get()
      .transform {
        case Success(r) if r.status == Status.OK => Success(r.body[JsValue].as[Output])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }
  }

  def list(urlFragments: String = "")(implicit ec: ExecutionContext, auth: Authentication): Future[Seq[Output]] = {
    logger.debug(s"Request GET $baseUrl")
    auth(ws.url(s"$baseUrl$urlFragments"))
      .get
      .transform {
        case Success(r) if r.status == Status.OK => Success(r.body[JsValue].as[Seq[Output]])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }
  }
}
