package org.thp.thehive

import scala.concurrent.{ExecutionContext, Promise}

import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.{Helpers, PlaySpecification, TestServer}

import com.typesafe.config.ConfigFactory

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
      val resp     = createInitialUser(initialUserLogin, initialUserName, initialUserPassword)
      val expected = Json.obj("login" → initialUserLogin, "name" → initialUserName, "permissions" → Seq("read", "write", "admin"))
      await(resp) must JsonMatcher(expected)
    }

    "create new user" in {
      val resp     = createUser("toom", "Thomas", Seq("read", "write"), "secret")
      val expected = Json.obj("login" → "toom", "name" → "Thomas", "permissions" → Seq("read", "write"))
      await(resp) must_=== expected
    }

    "list users" in {
      val resp = listUser
      val expected = Json.arr(
        Json.obj("login" → initialUserLogin, "name" → initialUserName, "permissions" → Seq("read", "write", "admin")),
        Json.obj("login" → "toom", "name"           → "Thomas", "permissions"         → Seq("read", "write"))
      )
      await(resp) must_=== expected
    }

    "return an authentication error if password is wrong" in {
      val resp = listUser(ec, initialUserAuth.copy(password="nopassword"))
      val expected = ApplicationError(401, Json.obj(
          "type"  -> "AuthenticationError",
          "message"  ->  "Authentication failure"))
      await(resp) must throwA(expected)
    }

    "create a simple case" in {
      val resp = createCase("First case", "This case is the first case of functional tests")
      val expected = Json.obj("title" -> "First case", "description" -> "This case is the first case of functional tests")
      /*
      {
  "_id" : "ef2cf6b1-1339-4431-adc8-9db71cd3bc6d",
  "_createdBy" : "admin",
  "_createdAt" : 1536159474918,
  "number" : 1,
  "title" : "First case",
  "description" : "This case is the first case of functional tests",
  "severity" : 2,
  "startDate" : 1536159474221,
  "endDate" : null,
  "tags" : [ ],
  "flag" : false,
  "tlp" : 2,
  "pap" : 2,
  "status" : "open"
}
       */
      await(resp) must_=== expected
    }

    "stop the application" in {
      server.stop()
      1 must_=== 1
    }
  }
}