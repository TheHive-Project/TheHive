package org.thp.thehive
import java.util.Date

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import play.api.Application
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.{WSAuthScheme, WSClient, WSResponse}
import play.api.test.TestServer

import org.specs2.matcher.{ContainWithResultSeq, Expectable, MatchResult, MatchResultLogicalCombinators, Matcher, TraversableMatchers, ValueCheck}
import org.thp.thehive.models.CaseStatus

case class ApplicationError(status: Int, body: JsValue) extends Exception(s"ApplicationError($status):\n${Json.prettyPrint(body)}")
object ApplicationError {
  def apply(r: WSResponse): ApplicationError = ApplicationError(r.status, r.body[JsValue])
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
        case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue])
        case Success(r)                               ⇒ Failure(ApplicationError(r))
        case Failure(t)                               ⇒ throw t
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
        case Success(r) if r.status == Status.CREATED ⇒ Success(r.body[JsValue])
        case Success(r)                               ⇒ Failure(ApplicationError(r))
        case Failure(t)                               ⇒ throw t
      }

  def getUser(login: String)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user/$login")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue])
        case Success(r)                          ⇒ Failure(ApplicationError(r))
        case Failure(t)                          ⇒ throw t
      }

  def listUser(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] =
    ws.url(s"$baseUrl/api/v1/user")
      .withAuth(auth.username, auth.password, WSAuthScheme.BASIC)
      .get()
      .transform {
        case Success(r) if r.status == Status.OK ⇒ Success(r.body[JsValue])
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
      summary: Option[String] = None)(implicit ec: ExecutionContext, auth: Authentication): Future[JsValue] = {
    val body = Json.obj(
      "title"       → title,
      "description" → description,
      "severity"    → severity,
      "startDate"   → startDate,
      "endDate"     → endDate,
      "tags"        → tags,
      "flag"        → flag,
      "tlp"         → tlp,
      "pap"         → pap,
      "status"      → status,
      "summary"     → summary
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
}

case class JsonMatcher(expected: JsValue) extends Matcher[JsValue] with MatchResultLogicalCombinators {
  override def apply[S <: JsValue](t: Expectable[S]): MatchResult[S] =
    if (t.value == expected) success("ok", t)
    else {
      (expected, t.value) match {
        case (JsNull, JsNull)             ⇒ success("JsNull == JsNull", t)
        case (JsNumber(e), JsNumber(v))   ⇒ result(e == v, "ok", "ko", t)
        case (JsString(e), JsString(v))   ⇒ result(e == v, "ok", "ko", t)
        case (JsBoolean(e), JsBoolean(v)) ⇒ result(e == v, "ok", "ko", t)
        case (JsArray(e), JsArray(v)) ⇒
          val valueChecks: Seq[ValueCheck[JsValue]] = TraversableMatchers.matcherSeqIsContainCheckSeq(v.map(JsonMatcher))
          val mr                                    = ContainWithResultSeq(valueChecks).exactly.apply(t.map(e))
          result(mr, t)
        case (JsObject(e), JsObject(v)) ⇒
          val keys = e.keySet ++ v.keySet
          val mr = keys
            .map(k ⇒ e.get(k) → v.get(k))
            .map {
              case (Some(a), Some(b)) ⇒ JsonMatcher(a)(t.map(b))
            }
            .reduce(_ and _)
          result(mr, t)
        case (e, v) ⇒ failure(s"$e != $v", t)
      }
    }
}
