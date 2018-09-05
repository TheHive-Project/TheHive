package org.thp.thehive
import java.util.Date

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import play.api.Application
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}
import play.api.test.TestServer
import org.specs2.matcher.{ContainWithResultSeq, Expectable, MatchResult, MatchResultLogicalCombinators, Matcher, TraversableMatchers, ValueCheck}
import org.thp.thehive.models.CaseStatus

case class ApplicationError(status: Int, body: JsValue) extends Exception(s"ApplicationError($status):\n${Json.prettyPrint(body)}")
object ApplicationError {
  def apply(r: WSResponse): ApplicationError = ApplicationError(r.status, Try(r.body[JsValue]).getOrElse(Json.obj("body" → r.body)))
}
case class Authentication(username: String, password: String)

trait TestHelper {
  def server: TestServer
  lazy val app: Application = server.application
  lazy val ws: WSClient     = app.injector.instanceOf[WSClient]
  def baseUrl: String       = s"http://127.0.0.1:${server.runningHttpPort.get}"

  def createInitialUser(login: String, username: String, password: String)(implicit ec: ExecutionContext): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user")
      .post(
        Json.obj(
          "login"       → login,
          "name"        → username,
          "password"    → password,
          "permissions" → Seq("read", "write", "admin")
        ))
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒
          Try(r.body[JsValue]).recoverWith { case _ ⇒ sys.error(s"Response body is invalid:\n${r.body}") }
        case Success(r) ⇒ Failure(ApplicationError(r))
        case Failure(t) ⇒ throw t
      }

  def createUser(login: String, username: String, permission: Seq[String], password: String)(
      implicit ec: ExecutionContext,
      auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .post(
        Json.obj(
          "login"       → login,
          "name"        → username,
          "password"    → password,
          "permissions" → permission
        ))
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒
          Try(r.body[JsValue]).recoverWith { case _ ⇒ sys.error(s"Response body is invalid:\n${r.body}") }
        case Success(r) ⇒ Failure(ApplicationError(r))
        case Failure(t) ⇒ throw t
      }

  def getUser(login: String)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user/$login")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Try(r.body[JsValue]).recoverWith { case _ ⇒ sys.error(s"Response body is invalid:\n${r.body}") }
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }

  def listUser(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Try(r.body[JsValue]).recoverWith { case _ ⇒ sys.error(s"Response body is invalid:\n${r.body}") }
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }

  def createCase(
      title: String,
      description: String,
      severity: Option[Int] = None,
      startDate: Option[Date] = None,
      endDate: Option[Date] = None,
      tags: Seq[String] = Nil,
      flag: Option[Boolean] = None,
      tlp: Option[Int] = None,
      pap: Option[Int] = None,
      status: Option[CaseStatus.Value] = None,
      summary: Option[String] = None,
      user: Option[String] = None,
      customFields: JsObject = JsObject.empty)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] = {
    val body = Json.obj(
      "title"        → title,
      "description"  → description,
      "severity"     → severity,
      "startDate"    → startDate,
      "endDate"      → endDate,
      "tags"         → tags,
      "flag"         → flag,
      "tlp"          → tlp,
      "pap"          → pap,
      "status"       → status,
      "summary"      → summary,
      "user"         → user,
      "customFields" → customFields
    )

    ws.url(s"$baseUrl/api/v1/case")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .post(JsObject(body.fields.filterNot(_._2 == JsNull))) // Remove null values
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue])
        case Success(r)                               ⇒ Failure(ApplicationError(r))
        case Failure(t)                               ⇒ throw t
      }
  }

  def getCase(caseId: String)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/case/$caseId")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }

  def listCase(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/case")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }

  def createCustomField(name: String, description: String, `type`: String)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/customField")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .post(
        Json.obj(
          "name"        → name,
          "description" → description,
          "type"        → `type`
        ))
      .transform {
        case Success(r) if r.status == Status.CREATED ⇒
          Try(r.body[JsValue]).recoverWith { case _ ⇒ sys.error(s"Response body is invalid:\n${r.body}") }
        case Success(r) ⇒ Failure(ApplicationError(r))
        case Failure(t) ⇒ throw t
      }
}

case class JsonMatcher(expected: JsValue) extends Matcher[JsValue] with MatchResultLogicalCombinators {
  override def apply[S <: JsValue](t: Expectable[S]): MatchResult[S] =
    if (t.value == expected) success("ok", t)
    else {
      (expected, t.value) match {
        case (JsArray(exp), JsArray(value)) ⇒
          val valueChecks: Seq[ValueCheck[JsValue]] = TraversableMatchers.matcherSeqIsContainCheckSeq(value.map(JsonMatcher))
          val expectable: Expectable[Seq[JsValue]]  = t.map(exp)
          val matchResult                           = ContainWithResultSeq(valueChecks).exactly(expectable)
          result(matchResult, t)
        case (JsObject(exp), JsObject(value)) ⇒
          val keys = exp.keySet ++ value.keySet
          val matchResult = keys
            .map(key ⇒ (key, exp.get(key), value.get(key)))
            .map {
              case (key, Some(e), Some(v)) ⇒ JsonMatcher(e)(t.map(v).mapDescription(_ + "." + key))
              case (key, None, Some(v))    ⇒ failure(s"${t.description} has key $key", t.map(v))
              case (key, _, _)             ⇒ failure(s"${t.description} hasn't key $key", t)
            }
            .reduce(_ and _)
          result(matchResult, t)
        case (_, v) ⇒ failure(s"${t.description} !=  $v", t)
      }
    }
}
