package org.thp.thehive

import scala.concurrent.{ExecutionContext, Promise}

import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.test.{Helpers, PlaySpecification, TestServer}

import com.typesafe.config.ConfigFactory
import org.thp.thehive.client.{ApplicationError, Authentication, TheHiveClient}
import org.thp.thehive.dto.v1._

class FunctionalTest extends PlaySpecification {

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
    implicit lazy val ws: WSClient                    = app.injector.instanceOf[WSClient]
    lazy val client                                   = new TheHiveClient(s"http://127.0.0.1:${server.runningHttpPort.get}")

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

    var user1: OutputUser = null
    "create initial user" in {
      val asyncResp =
        client.user.createInitial(InputUser(initialUserLogin, initialUserName, Seq("read", "write", "admin"), Some(initialUserPassword)))
      user1 = await(asyncResp)
      val expected = OutputUser(initialUserLogin, initialUserName, Set("read", "write", "admin"))
      user1 must_=== expected
    }

    var user2: OutputUser = null
    "create new user" in {
      val asyncResp = client.user.create(InputUser("toom", "Thomas", Seq("read", "write"), Some("secret")))
      user2 = await(asyncResp)
      val expected = OutputUser("toom", "Thomas", Set("read", "write"))
      user2 must_=== expected
    }

    "list users" in {
      val asyncResp = client.user.list
      await(asyncResp) must contain(exactly(user1, user2))
    }

    "return an authentication error if password is wrong" in {
      val asyncResp = client.user.list(ec, initialUserAuth.copy(password = "nopassword"))
      val expected  = ApplicationError(401, Json.obj("type" → "AuthenticationError", "message" → "Authentication failure"))
      await(asyncResp) must throwA(expected)
    }

    var case1: OutputCase = null
    "create a simple case" in {
      val asyncResp = client.`case`.create(InputCase("First case", "This case is the first case of functional tests"))
      case1 = await(asyncResp)
      val expected = OutputCase(
        _id = case1._id,
        _createdBy = "admin",
        _createdAt = case1._createdAt,
        number = 1,
        title = "First case",
        description = "This case is the first case of functional tests",
        severity = 2,
        startDate = case1.startDate,
        flag = false,
        tlp = 2,
        pap = 2,
        status = "open",
        user = "admin"
      )

      case1 must_=== expected
    }

    "create a custom field" in {
      val asyncResp = client.customFields.create(InputCustomField("businessUnit", "Business unit impacted by the incident", "string"))
      val expected  = OutputCustomField("businessUnit", "Business unit impacted by the incident", "string")
      await(asyncResp) must_=== expected
    }

    var case2: OutputCase = null
    "create a case with custom fields" in {
      val asyncResp = client.`case`.create(
        InputCase(
          title = "Second case",
          description = "This case contains status, summary and custom fields",
          status = Some("resolved"),
          summary = Some("no comment"),
          customFieldValue = Seq(InputCustomFieldValue("businessUnit", "HR"))
        ))
      case2 = await(asyncResp)
      val expected = OutputCase(
        _id = case2._id,
        _createdBy = "admin",
        _createdAt = case2._createdAt,
        number = 2,
        title = "Second case",
        description = "This case contains status, summary and custom fields",
        severity = 2,
        startDate = case2.startDate,
        flag = false,
        tlp = 2,
        pap = 2,
        status = "open",
        user = "admin",
        summary = Some("no comment"),
        customFields = Set(OutputCustomFieldValue("businessUnit", "Business unit impacted by the incident", "string", "HR"))
      )
      case2 must_=== expected
    }

    "list cases" in {
      val asyncResp = client.`case`.list
      await(asyncResp) must contain(exactly(case1, case2))
    }

    "stop the application" in {
      server.stop()
      1 must_=== 1
    }
  }
}
