package org.thp.thehive.client

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSClient

import org.thp.client.{ApplicationError, Authentication, BaseClient}
import org.thp.thehive.dto.v1._

class TheHiveClient(baseUrl: String)(implicit ws: WSClient) {
  lazy val logger  = Logger(getClass)
  val `case`       = new BaseClient[InputCase, OutputCase](s"$baseUrl/api/v1/case")
  val user         = new BaseClient[InputUser, OutputUser](s"$baseUrl/api/v1/user")
  val customFields = new BaseClient[InputCustomField, OutputCustomField](s"$baseUrl/api/v1/customField")
  val organisation = new BaseClient[InputOrganisation, OutputOrganisation](s"$baseUrl/api/v1/organisation")
//  val share        = new BaseClient[InputShare, OutputShare](s"$baseUrl/api/v1/share")
  val task  = new BaseClient[InputTask, OutputTask](s"$baseUrl/api/v1/task")
  val alert = new BaseClient[InputAlert, OutputAlert](s"$baseUrl/api/v1/alert")

  object audit {

    def list(implicit ec: ExecutionContext, auth: Authentication): Future[Seq[OutputAudit]] = {
      logger.debug(s"Request GET $baseUrl")
      auth(ws.url(s"$baseUrl/api/v1/audit"))
        .get()
        .transform {
          case Success(r) if r.status == Status.OK => Success(r.body[JsValue].as[Seq[OutputAudit]])
          case Success(r)                          => Failure(ApplicationError(r))
          case Failure(t)                          => throw t
        }
    }
  }

  def query(q: JsObject*)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    auth(ws.url(s"$baseUrl/api/v1/query"))
      .post(Json.obj("query" -> q))
      .transform {
        case Success(r) if r.status == Status.OK => Success(r.body[JsValue])
        case Success(r)                          => Failure(ApplicationError(r))
        case Failure(t)                          => throw t
      }
}
