package org.thp.thehive

import scala.concurrent.{ExecutionContext, Promise}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsString, Json}
import play.api.test.{Helpers, PlaySpecification, TestServer}
import com.typesafe.config.ConfigFactory
import org.thp.thehive.models.CaseStatus

class FunctionalTest extends PlaySpecification with TestHelper {

  val serverPromise: Promise[TestServer] = Promise[TestServer]
  lazy val server: TestServer            = serverPromise.future.value.get.get

  sequential

  val config = Configuration(ConfigFactory.parseString("""
      |db.provider: janusgraph
    """.stripMargin))

  "TheHive" should {
    lazy val app                                      = server.application
    lazy val initialUserLogin                         = "admin"
    lazy val initialUserName                          = "Administrator"
    lazy val initialUserPassword                      = "azerty"
    implicit lazy val initialUserAuth: Authentication = Authentication(initialUserLogin, initialUserPassword)
    implicit lazy val ec: ExecutionContext            = app.injector.instanceOf[ExecutionContext]

    "start the application" in {
      serverPromise.success(
        TestServer(
          port = Helpers.testServerPort,
          application = GuiceApplicationBuilder()
            .configure(config)
            .build()))
      server.start()
      1 must_=== 1
    }

    "create initial user" in {
      val asyncResp = createInitialUser(initialUserLogin, initialUserName, initialUserPassword)
      val expected  = Json.obj("login" → initialUserLogin, "name" → initialUserName, "permissions" → Seq("read", "write", "admin"))
      await(asyncResp) must JsonMatcher(expected)
    }

    "create new user" in {
      val asyncResp = createUser("toom", "Thomas", Seq("read", "write"), "secret")
      val expected  = Json.obj("login" → "toom", "name" → "Thomas", "permissions" → Seq("read", "write"))
      await(asyncResp) must JsonMatcher(expected)
    }

    "list users" in {
      val asyncResp = listUser
      val expected = Json.arr(
        Json.obj("login" → initialUserLogin, "name" → initialUserName, "permissions" → Seq("read", "write", "admin")),
        Json.obj("login" → "toom", "name"           → "Thomas", "permissions"        → Seq("read", "write"))
      )
      await(asyncResp) must JsonMatcher(expected)
    }

    "return an authentication error if password is wrong" in {
      val asyncResp = listUser(ec, initialUserAuth.copy(password = "nopassword"))
      val expected  = ApplicationError(401, Json.obj("type" → "AuthenticationError", "message" → "Authentication failure"))
      await(asyncResp) must throwA(expected)
    }

    "create a simple case" in {
      val asyncResp = createCase("First case", "This case is the first case of functional tests")
      val resp      = await(asyncResp)
      val expected = Json.obj(
        "title"        → "First case",
        "description"  → "This case is the first case of functional tests",
        "_createdBy"   → "admin",
        "number"       → 1,
        "severity"     → 2,
        "tags"         → JsArray(),
        "flag"         → false,
        "tlp"          → 2,
        "pap"          → 2,
        "status"       → "open",
        "user"         → "admin",
        "_updatedBy"   → JsNull,
        "_updatedAt"   → JsNull,
        "summary"      → JsNull,
        "endDate"      → JsNull,
        "customFields" → JsArray()
      ) +
        ("_id"        → (resp \ "_id").as[JsString]) +
        ("_createdAt" → (resp \ "_createdAt").as[JsNumber]) +
        ("startDate"  → (resp \ "startDate").as[JsNumber])
      resp must JsonMatcher(expected)
    }

    "create a custom field" in {
      val asyncResp = createCustomField("businessUnit", "Business unit impacted by the incident", "string")
      val expected  = Json.obj("name" → "businessUnit", "description" → "Business unit impacted by the incident", "type" → "string")
      await(asyncResp) must_=== expected
    }

    "create a case with custom fields" in {
      val asyncResp = createCase(
        "Second case",
        "This case contains status, summary and custom fields",
        status = Some(CaseStatus.resolved),
        summary = Some("no comment"),
        customFields = Json.obj("businessUnit" → "HR")
      )
      val resp = await(asyncResp)
      val expected = Json.obj(
        "title"       → "Second case",
        "description" → "This case contains status, summary and custom fields",
        "_createdBy"  → "admin",
        "number"      → 2,
        "severity"    → 2,
        "tags"        → JsArray(),
        "flag"        → false,
        "tlp"         → 2,
        "pap"         → 2,
        "status"      → "open",
        "summary"     → "no comment",
        "user"        → "admin",
        "endDate"     → JsNull,
        "_updatedBy"  → JsNull,
        "_updatedAt"  → JsNull,
        "customFields" → Json.arr(
          Json.obj("name" → "businessUnit", "description" → "Business unit impacted by the incident", "type" → "string", "value" → "HR"))
      ) +
        ("_id"        → (resp \ "_id").as[JsString]) +
        ("_createdAt" → (resp \ "_createdAt").as[JsNumber]) +
        ("startDate"  → (resp \ "startDate").as[JsNumber])
      resp must JsonMatcher(expected)
    }
    "stop the application" in {
      server.stop()
      1 must_=== 1
    }
  }
}
