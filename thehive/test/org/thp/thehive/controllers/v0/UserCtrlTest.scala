package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.scalligraph.AuthenticationError
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.services.UserSrv

case class TestUser(login: String, name: String, roles: Set[String], organisation: String, hasKey: Boolean, status: String)

object TestUser {

  def apply(user: OutputUser): TestUser =
    TestUser(user.login, user.name, user.roles, user.organisation, user.hasKey, user.status)
}

class UserCtrlTest extends PlaySpecification with TestAppBuilder {
  "user controller" should {
    "search users" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
        .withJsonBody(
          Json.parse(
            """{"query": {"_and": [{"status": "Ok"}, {"_not": {"_is": {"login": "socadmin@thehive.local"}}}, {"_not": {"_is": {"login": "socuser@thehive.local"}}}]}}"""
          )
        )
        .withHeaders("user" -> "socadmin@thehive.local")

      val result = app[TheHiveQueryExecutor].user.search(request)
      status(result) must_=== 200

      val resultUsers = contentAsJson(result)
      val expected =
        Seq(
          TestUser(
            login = "socro@thehive.local",
            name = "socro",
            roles = Set("read"),
            organisation = "soc",
            hasKey = false,
            status = "Ok"
          )
        )

      resultUsers.as[Seq[OutputUser]].map(TestUser.apply) shouldEqual expected
    }

    "create a new user" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/user")
        .withJsonBody(Json.parse("""{"login": "certXX@thehive.local", "name": "new user", "roles": ["read", "write", "alert"]}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[UserCtrl].create(request)
      status(result) must_=== 201

      val resultUser = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "certXX@thehive.local",
        name = "new user",
        roles = Set("read", "write", "alert"),
        organisation = "cert",
        hasKey = false,
        status = "Ok"
      )

      TestUser(resultUser) must_=== expected
    }

    "update a user" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/user/certuser@thehive.local")
        .withJsonBody(Json.parse("""{"name": "new name"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[UserCtrl].update("certuser@thehive.local")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.name must_=== "new name"
    }

    "lock an user" in testApp { app =>
      val authRequest1 = FakeRequest("POST", "/api/v0/login")
        .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
      val authResult1 = app[AuthenticationCtrl].login(authRequest1)
      status(authResult1) must_=== 200

      val request = FakeRequest("POST", "/api/v0/user/certuser@thehive.local")
        .withJsonBody(Json.parse("""{"status": "Locked"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[UserCtrl].update("certuser@thehive.local")(request)
      status(result) must_=== 200
      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.status must_=== "Locked"

      // then authentication must fail
      val authRequest2 = FakeRequest("POST", "/api/v0/login")
        .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
      val authResult2 = app[AuthenticationCtrl].login(authRequest2)
      status(authResult2) must_=== 401
    }

    "unlock an user" in testApp { app =>
      val keyAuthRequest = FakeRequest("GET", "/api/v0/user/current")
        .withHeaders("Authorization" -> "Bearer azertyazerty")

      status(app[UserCtrl].current(keyAuthRequest)) must throwA[AuthenticationError]

      val request = FakeRequest("POST", "/api/v0/user/certro@thehive.local")
        .withJsonBody(Json.parse("""{"status": "Ok"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[UserCtrl].update("certro@thehive.local")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.status must_=== "Ok"

      status(app[UserCtrl].current(keyAuthRequest)) must_=== 200
    }

    "remove a user (lock)" in testApp { app =>
      val request = FakeRequest("DELETE", "/api/v0/user/certro@thehive.local")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = app[UserCtrl].lock("certro@thehive.local")(request)

      status(result) must beEqualTo(204)

      val requestGet = FakeRequest("POST", "/api/v0/user/certro@thehive.local")
        .withHeaders("user" -> "certadmin@thehive.local")
      val resultGet = app[UserCtrl].get("certro@thehive.local")(requestGet)

      status(resultGet) must_=== 200
      contentAsJson(resultGet).as[OutputUser].status must beEqualTo("Locked")
    }

    "remove a user (force)" in testApp { app =>
      val request = FakeRequest("DELETE", "/api/v0/user/certro@thehive.local/force")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = app[UserCtrl].delete("certro@thehive.local")(request)

      status(result) must beEqualTo(204)

      app[Database].roTransaction { implicit graph =>
        app[UserSrv].get("certro@thehive.local").exists()
      } must beFalse
    }
  }
}
