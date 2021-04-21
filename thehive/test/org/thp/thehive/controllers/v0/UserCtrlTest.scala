package org.thp.thehive.controllers.v0

import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AuthenticationError, EntityName}
import org.thp.thehive.dto.v0.OutputUser
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

case class TestUser(login: String, name: String, roles: Set[String], organisation: String, hasKey: Boolean, status: String)

object TestUser {

  def apply(user: OutputUser): TestUser =
    TestUser(user.login, user.name, user.roles, user.organisation, user.hasKey, user.status)
}

class UserCtrlTest extends PlaySpecification with TestAppBuilder {
  "user controller" should {
    "search users" in testApp { app =>
      import app._
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
        .withJsonBody(
          Json.parse(
            """{"query": {"_and": [{"status": "Ok"}, {"_not": {"_is": {"login": "socadmin@thehive.local"}}}, {"_not": {"_is": {"login": "socuser@thehive.local"}}}]}}"""
          )
        )
        .withHeaders("user" -> "socadmin@thehive.local")

      val result = userCtrl.search(request)
      status(result) must_=== 200

      val resultUsers = contentAsJson(result)(defaultAwaitTimeout, materializer)
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
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/v0/user")
        .withJsonBody(Json.parse("""{"login": "certXX@thehive.local", "name": "new user", "roles": ["read", "write", "alert"]}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = userCtrl.create(request)
      status(result) must_=== 201

      val resultUser = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "certxx@thehive.local",
        name = "new user",
        roles = Set("read", "write", "alert"),
        organisation = "cert",
        hasKey = false,
        status = "Ok"
      )

      TestUser(resultUser) must_=== expected
    }

    "update a user" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/v0/user/certuser@thehive.local")
        .withJsonBody(Json.parse("""{"name": "new name"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = userCtrl.update("certuser@thehive.local")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.name must_=== "new name"
    }

    "lock an user" in testApp { app =>
      import app.thehiveModuleV0._

      val authRequest1 = FakeRequest("POST", "/api/v0/login")
        .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
      val authResult1 = authenticationCtrl.login(authRequest1)
      status(authResult1) must_=== 200

      val request = FakeRequest("POST", "/api/v0/user/certuser@thehive.local")
        .withJsonBody(Json.parse("""{"status": "Locked"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = userCtrl.update("certuser@thehive.local")(request)
      status(result) must_=== 200
      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.status must_=== "Locked"

      // then authentication must fail
      val authRequest2 = FakeRequest("POST", "/api/v0/login")
        .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
      val authResult2 = authenticationCtrl.login(authRequest2)
      status(authResult2) must_=== 401
    }

    "unlock an user" in testApp { app =>
      import app.thehiveModuleV0._

      val keyAuthRequest = FakeRequest("GET", "/api/v0/user/current")
        .withHeaders("Authorization" -> "Bearer azertyazerty")

      status(userCtrl.current(keyAuthRequest)) must throwA[AuthenticationError]

      val request = FakeRequest("POST", "/api/v0/user/certro@thehive.local")
        .withJsonBody(Json.parse("""{"status": "Ok"}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = userCtrl.update("certro@thehive.local")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultUser = contentAsJson(result).as[OutputUser]
      resultUser.status must_=== "Ok"

      status(userCtrl.current(keyAuthRequest)) must_=== 200
    }

    "remove a user (lock)" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("DELETE", "/api/v0/user/certro@thehive.local")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = userCtrl.lock("certro@thehive.local")(request)

      status(result) must beEqualTo(204)

      val requestGet = FakeRequest("POST", "/api/v0/user/certro@thehive.local")
        .withHeaders("user" -> "certadmin@thehive.local")
      val resultGet = userCtrl.get("certro@thehive.local")(requestGet)

      status(resultGet) must_=== 200
      contentAsJson(resultGet).as[OutputUser].status must beEqualTo("Locked")
    }

    "remove a user (force)" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val request = FakeRequest("DELETE", "/api/v0/user/certro@thehive.local/force")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = userCtrl.delete("certro@thehive.local")(request)

      status(result) must beEqualTo(204)

      database.roTransaction { implicit graph =>
        userSrv.get(EntityName("certro@thehive.local")).exists
      } must beFalse
    }
  }
}
