package org.thp.thehive.client
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import play.api.http.Status
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{WSAuthScheme, WSClient}

import org.thp.thehive.dto.v1._

class UserClient(baseUrl: String)(implicit ws: WSClient) extends BaseClient[InputUser, OutputUser](s"$baseUrl/api/v1/user") {
  def createInitial(user: InputUser)(implicit ec: ExecutionContext): Future[OutputUser] = {
    val body = Json.toJson(user)
    Client.logger.debug(s"Request POST $baseUrl/api/v1/user\n${Json.prettyPrint(body)}")
    ws.url(s"$baseUrl/api/v1/user")
      .post(body)
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue].as[OutputUser])
        case Success(r)                               ⇒ Failure(ApplicationError(r))
        case Failure(t)                               ⇒ throw t
      }
  }
}

class TheHiveClient(baseUrl: String)(implicit ws: WSClient) {
  val `case`       = new BaseClient[InputCase, OutputCase](s"$baseUrl/api/v1/case")
  val user         = new UserClient(baseUrl)
  val customFields = new BaseClient[InputCustomField, OutputCustomField](s"$baseUrl/api/v1/customField")
  val organisation = new BaseClient[InputOrganisation, OutputOrganisation](s"$baseUrl/api/v1/organisation")
  val share        = new BaseClient[InputShare, OutputShare](s"$baseUrl/api/v1/share")
  val task         = new BaseClient[InputTask, OutputTask](s"$baseUrl/api/v1/task")
  def query(q: JsObject*)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/query")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .post(Json.obj("query" → q))
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }
}
