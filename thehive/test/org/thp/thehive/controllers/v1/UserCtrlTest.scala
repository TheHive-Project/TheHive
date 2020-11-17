package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import scala.util.{Success, Try}

case class TestUser(login: String, name: String, profile: String, permissions: Set[String], organisation: String)

object TestUser {

  def apply(user: OutputUser): TestUser =
    TestUser(user.login, user.name, user.profile, user.permissions, user.organisation)
}

class DummyAuthSrv extends AuthSrv {
  val name: String                                                                                              = "dummy"
  override def setPassword(username: String, newPassword: String)(implicit authContext: AuthContext): Try[Unit] = Success(())
}

class UserCtrlTest extends PlaySpecification with TestAppBuilder {
  "user controller" should {

    "return current user information" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/user/current")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[UserCtrl].current(request)
      status(result) must_=== 200
      val resultCase = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "admin@thehive.local",
        name = "Default admin user",
        profile = "admin",
        permissions = Permissions.adminPermissions.map(_.toString),
        organisation = Organisation.administration.name
      )

      TestUser(resultCase) must_=== expected
    }

    "create a new user" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/user")
        .withJsonBody(
          Json.toJson(
            InputUser(
              login = "test_user_1@thehive.local",
              name = "create user test",
              password = Some("azerty"),
              profile = "read-only",
              organisation = Some(Organisation.administration.name),
              avatar = None
            )
          )
        )
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[UserCtrl].create(request)
      status(result) must_=== 201
      val resultCase = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "test_user_1@thehive.local",
        name = "create user test",
        profile = "read-only",
        permissions = Set.empty,
        organisation = Organisation.administration.name
      )

      TestUser(resultCase) must_=== expected
    }

    "refuse to create an user if the permission doesn't contain ManageUser right" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/user")
        .withJsonBody(
          Json.toJson(
            InputUser(
              login = "test_user_3@thehive.local",
              name = "create user test",
              password = Some("azerty"),
              profile = "analyst",
              organisation = Some("cert"),
              avatar = None
            )
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[UserCtrl].create(request)
      status(result) must beEqualTo(403).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "get a user in the same organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/user/certadmin@thehive.local").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[UserCtrl].get("certadmin@thehive.local")(request)
      status(result) must_=== 200
      val resultCase = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "certadmin@thehive.local",
        name = "certadmin",
        profile = Profile.orgAdmin.name,
        permissions = Set(
          Permissions.manageShare,
          Permissions.manageAnalyse,
          Permissions.manageTask,
          Permissions.manageCaseTemplate,
          Permissions.manageCase,
          Permissions.manageUser,
          Permissions.managePage,
          Permissions.manageObservable,
          Permissions.manageAlert,
          Permissions.manageTaxonomy,
          Permissions.manageAction,
          Permissions.manageConfig,
          Permissions.accessTheHiveFS
        ),
        organisation = "cert"
      )

      TestUser(resultCase) must_=== expected
    }

    "get a user of a visible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/user/socuser@thehive.local").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[UserCtrl].get("socuser@thehive.local")(request)
      status(result) must_=== 200
      val resultCase = contentAsJson(result).as[OutputUser]
      val expected = TestUser(
        login = "socuser@thehive.local",
        name = "socuser",
        profile = "analyst",
        permissions = Profile.analyst.permissions.map(_.toString),
        organisation = "soc"
      )

      TestUser(resultCase) must_=== expected
    } //.pendingUntilFixed("Organisation visibility needs to be fixed")

    "refuse to get a user of an invisible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/user/admin@thehive.local").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[UserCtrl].get("admin@thehive.local")(request)
      status(result) must_=== 404
    }

    "update an user" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/user/certuser@thehive.local")
        .withJsonBody(Json.parse("""{"name": "new name", "roles": ["read"]}"""))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[UserCtrl].update("certuser@thehive.local")(request)
      status(result) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "set password" in testApp { app =>
      val requestSetPassword = FakeRequest("POST", s"/user/certuser@thehive.local/password/set")
        .withJsonBody(Json.obj("password" -> "mySecretPassword"))
        .withHeaders("user" -> "user2@thehive.local")
      val resultSetPassword = app[UserCtrl].setPassword("certuser@thehive.local")(requestSetPassword)
      status(resultSetPassword) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(resultSetPassword)}")

      val request = FakeRequest("GET", "/api/v1/login")
        .withJsonBody(Json.obj("user" -> "certuser@thehive.local", "password" -> "mySecretPassword"))
      val resultAuth = app[AuthenticationCtrl].login()(request)
      status(resultAuth) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultAuth)}")
    }.pendingUntilFixed("need an admin user in cert organisation")

    "change password" in pending
    "get key" in testApp { app =>
      val requestRenew = FakeRequest("POST", s"/user/user2@thehive.local/key/renew").withHeaders("user" -> "user2@thehive.local")
      val resultRenew  = app[UserCtrl].renewKey("user2@thehive.local")(requestRenew)
      status(resultRenew) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultRenew)}")
      val key = contentAsString(resultRenew)
      key.length must beEqualTo(32)

      val requestGet = FakeRequest("GET", s"/user/user2@thehive.local/key").withHeaders("user" -> "user2@thehive.local")
      val resultGet  = app[UserCtrl].getKey("user2@thehive.local")(requestGet)
      status(resultGet) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
      val newKey = contentAsString(resultGet)
      newKey must beEqualTo(key)
    }.pendingUntilFixed("need an admin user in cert organisation")

    "remove key" in testApp { app =>
      val requestRenew = FakeRequest("POST", s"/user/certuser@thehive.local/key/renew").withHeaders("user" -> "user2@thehive.local")
      val resultRenew  = app[UserCtrl].renewKey("certuser@thehive.local")(requestRenew)
      status(resultRenew) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultRenew)}")
      val key = contentAsString(resultRenew)
      key.length must beEqualTo(32)

      val requestRemove = FakeRequest("DELETE", s"/user/certuser@thehive.local/key").withHeaders("user" -> "user2@thehive.local")
      val resultRemove  = app[UserCtrl].removeKey("certuser@thehive.local")(requestRemove)
      status(resultRemove) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(resultRemove)}")

      val requestGet = FakeRequest("GET", s"/user/certuser@thehive.local/key").withHeaders("user" -> "user2@thehive.local")
      val resultGet  = app[UserCtrl].getKey("certuser@thehive.local")(requestGet)
      status(resultGet) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
    }.pendingUntilFixed("need an admin user in cert organisation")

    "renew key" in pending
  }
}
