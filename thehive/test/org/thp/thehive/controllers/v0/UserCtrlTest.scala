package org.thp.thehive.controllers.v0

import scala.util.Try
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.{AppBuilder, AuthenticationError}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.models._

case class TestUser(login: String, name: String, roles: Set[String], organisation: String, hasKey: Boolean, status: String)

object TestUser {

  def apply(user: OutputUser): TestUser =
    TestUser(user.login, user.name, user.roles, user.organisation, user.hasKey, user.status)
}

class UserCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = ()

  def specs(name: String, app: AppBuilder): Fragment = {
    val userCtrl: UserCtrl   = app.instanceOf[UserCtrl]
    val authenticationCtrl   = app.instanceOf[AuthenticationCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    s"[$name] user controller" should {

      "search users" in {
        val request = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
          .withJsonBody(
            Json.parse(
              """{"query": {"_and": [{"status": "Ok"}, {"_not": {"_is": {"login": "user4@thehive.local"}}}, {"_not": {"_is": {"login": "user2@thehive.local"}}}]}}"""
            )
          )
          .withHeaders("user" -> "user1@thehive.local")

        val result = theHiveQueryExecutor.user.search(request)
        status(result) must_=== 200

        val resultUsers = contentAsJson(result)
        val expected =
          Seq(
            TestUser(
              login = "user1@thehive.local",
              name = "Thomas",
              roles = Set("read", "write", "alert"),
              organisation = "cert",
              hasKey = false,
              status = "Ok"
            )
          )

        resultUsers.as[Seq[OutputUser]].map(TestUser.apply) shouldEqual expected
      }

      "create a new user" in {
        val request = FakeRequest("POST", "/api/v0/user")
          .withJsonBody(Json.parse("""{"login": "user5@thehive.local", "name": "new user", "roles": ["read", "write", "alert"]}"""))
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")

        val result = userCtrl.create(request)
        status(result) must_=== 201

        val resultUser = contentAsJson(result).as[OutputUser]
        val expected = TestUser(
          login = "user5@thehive.local",
          name = "new user",
          roles = Set("read", "write", "alert"),
          organisation = "admin",
          hasKey = false,
          status = "Ok"
        )

        TestUser(resultUser) must_=== expected
      }

      "update a user" in {
        val request = FakeRequest("POST", "/api/v0/user/user2@thehive.local")
          .withJsonBody(Json.parse("""{"name": "new name"}"""))
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")

        val result = userCtrl.update("user3@thehive.local")(request)
        status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.name must_=== "new name"
      }

      "lock an user" in {
        val authRequest1 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user2@thehive.local", "password": "my-secret-password"}"""))
        val authResult1 = authenticationCtrl.login(authRequest1)
        status(authResult1) must_=== 200

        val request = FakeRequest("POST", "/api/v0/user/user2@thehive.local")
          .withJsonBody(Json.parse("""{"status": "Locked"}"""))
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")

        val result = userCtrl.update("user2@thehive.local")(request)
        status(result) must_=== 200
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Locked"

        // then authentication must fail
        val authRequest2 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user2@thehive.local", "password": "my-secret-password"}"""))
        val authResult2 = authenticationCtrl.login(authRequest2)
        status(authResult2) must_=== 401
      }

      "unlock an user" in {
        val keyAuthRequest = FakeRequest("GET", "/api/v0/user/current")
          .withHeaders("Authorization" -> "Bearer azertyazerty")

        status(userCtrl.current(keyAuthRequest)) must throwA[AuthenticationError]

        val request = FakeRequest("POST", "/api/v0/user/user4@thehive.local")
          .withJsonBody(Json.parse("""{"status": "Ok"}"""))
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "cert")

        val result = userCtrl.update("user4@thehive.local")(request)
        status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Ok"

        status(userCtrl.current(keyAuthRequest)) must_=== 200
      }

      "remove a user" in {
        val request = FakeRequest("DELETE", "/api/v0/user/user2@thehive.local")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")

        val result = userCtrl.delete("user2@thehive.local")(request)

        status(result) must beEqualTo(204)

        val requestGet = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
          .withJsonBody(Json.parse("""{"query": {"_and": [{"_not": {"_is": {"login": "user4@thehive.local"}}}]}}"""))
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")

        val resultGet = theHiveQueryExecutor.user.search(requestGet)

        status(resultGet) must_=== 200

        val resultUser = contentAsJson(resultGet).as[Seq[OutputUser]].find(_.login == "user2@thehive.local").get

        resultUser.status must beEqualTo("Locked")
      }
    }
  }
}
