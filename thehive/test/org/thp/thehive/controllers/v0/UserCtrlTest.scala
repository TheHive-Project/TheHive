package org.thp.thehive.controllers.v0

import scala.util.Try

import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.models._

class UserCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
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
          .withJsonBody(Json.parse("""{"query": {"_and": [{"status": "Ok"}, {"_not": {"_is": {"login": "user4@thehive.local"}}}]}}"""))
          .withHeaders("user" -> "user1@thehive.local")

        val result = theHiveQueryExecutor.user.search(request)
        status(result) must_=== 200

        val resultUsers = contentAsJson(result)
        val expected =
          Seq(
            OutputUser(
              id = "user1@thehive.local",
              login = "user1@thehive.local",
              name = "Thomas",
              organisation = "cert",
              roles = Set("read", "write", "alert"),
              hasKey = Some(false)
            ),
            OutputUser(
              id = "user2@thehive.local",
              login = "user2@thehive.local",
              name = "U",
              organisation = "cert",
              roles = Set("read"),
              hasKey = Some(false)
            )
          )

        resultUsers.as[Seq[OutputUser]] shouldEqual expected
      }

      "create a new user" in {
        val request = FakeRequest("POST", "/api/v0/user")
          .withJsonBody(Json.parse("""{"login": "user5@thehive.local", "name": "new user", "roles": ["read", "write", "alert"]}"""))
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")

        val result = userCtrl.create(request)
        status(result) must_=== 201

        val resultUser = contentAsJson(result).as[OutputUser]
        val expected = OutputUser(
          id = "user5@thehive.local",
          login = "user5@thehive.local",
          name = "new user",
          organisation = "default",
          roles = Set("read", "write", "alert"),
          hasKey = Some(false)
        )

        resultUser must_=== expected
      }

      "update an user" in {
        val request = FakeRequest("POST", "/api/v0/user/user3@thehive.local")
          .withJsonBody(Json.parse("""{"name": "new name"}"""))
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")

        val result = userCtrl.update("user3@thehive.local")(request)
        status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.name must_=== "new name"
      }

      "lock an user" in {
        val authRequest1 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user3@thehive.local", "password": "my-secret-password"}"""))
        val authResult1 = authenticationCtrl.login(authRequest1)
        status(authResult1) must_=== 200

        val request = FakeRequest("POST", "/api/v0/user/user3@thehive.local")
          .withJsonBody(Json.parse("""{"status": "Locked"}"""))
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")

        val result = userCtrl.update("user3@thehive.local")(request)
        status(result) must_=== 200
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Locked"

        // then authentication must fail
        val authRequest2 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user3@thehive.local", "password": "my-secret-password"}"""))
        val authResult2 = authenticationCtrl.login(authRequest2)
        status(authResult2) must_=== 401
      }

      "unlock an user" in {
        val keyAuthRequest = FakeRequest("GET", "/api/v0/user/current")
          .withHeaders("Authorization" -> "Bearer azertyazerty")

        status(userCtrl.current(keyAuthRequest)) must_=== 401

        val request = FakeRequest("POST", "/api/v0/user/user4@thehive.local")
          .withJsonBody(Json.parse("""{"status": "Ok"}"""))
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "cert")

        val result = userCtrl.update("user4@thehive.local")(request)
        status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Ok"

        status(userCtrl.current(keyAuthRequest)) must_=== 200
      }

      "remove a user" in {
        val request = FakeRequest("DELETE", "/api/v0/user/user3@thehive.local")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")

        val result = userCtrl.delete("user3@thehive.local")(request)

        status(result) must beEqualTo(204)

        val requestGet = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
          .withJsonBody(Json.parse("""{"query": {"_and": [{"_not": {"_is": {"login": "user4@thehive.local"}}}]}}"""))
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")

        val resultGet = theHiveQueryExecutor.user.search(requestGet)

        status(resultGet) must_=== 200

        val resultUser = contentAsJson(resultGet).as[Seq[OutputUser]].find(_.login == "user3@thehive.local").get

        resultUser.status must beEqualTo("Locked")
      }
    }
  }
}
