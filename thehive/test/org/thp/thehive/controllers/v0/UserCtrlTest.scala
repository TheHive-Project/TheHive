package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, MultiAuthSrvProvider, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, DefaultAuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.models._
import org.thp.thehive.services.{LocalKeyAuthSrv, LocalPasswordAuthSrv, LocalUserSrv}

class UserCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv = DummyUserSrv(permissions = Permissions.all)

  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .multiBind[AuthSrv](classOf[LocalPasswordAuthSrv], classOf[LocalKeyAuthSrv])
      .bindToProvider[AuthSrv, MultiAuthSrvProvider]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = ()

  def specs(name: String, app: AppBuilder): Fragment = {
    val userCtrl: UserCtrl              = app.instanceOf[UserCtrl]
    val authenticationCtrl              = app.instanceOf[AuthenticationCtrl]
    val authenticateSrv                 = app.instanceOf[DefaultAuthenticateSrv]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] user controller" should {

      "search users" in {
        val request = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
          .withJsonBody(Json.parse("""{"query": {"_and": [{"status": "Ok"}, {"_not": {"_is": {"login": "user4"}}}]}}"""))
          .withHeaders("user" → "user1")

        val result = userCtrl.search(request)
        status(result) must_=== 200

        val resultUsers = contentAsJson(result)
        val expected =
          Seq(
            OutputUser(
              id = "user1",
              login = "user1",
              name = "Thomas",
              organisation = "cert",
              roles = Set("read", "write", "alert"),
              hasKey = Some(false)
            ),
            OutputUser(id = "user2", login = "user2", name = "U", organisation = "cert", roles = Set("read"), hasKey = Some(false))
          )

        resultUsers.as[Seq[OutputUser]] shouldEqual expected
      }

      "create a new user" in {
        val request = FakeRequest("POST", "/api/v0/user")
          .withJsonBody(Json.parse("""{"login": "user5", "name": "new user", "roles": ["read", "write", "alert"]}"""))
          .withHeaders("user" → "user2", "X-Organisation" → "default")

        val result = userCtrl.create(request)
        status(result) must_=== 201

        val resultUser = contentAsJson(result).as[OutputUser]
        val expected = OutputUser(
          id = "user5",
          login = "user5",
          name = "new user",
          organisation = "default",
          roles = Set("read", "write", "alert"),
          hasKey = Some(false)
        )

        resultUser must_=== expected
      }

      "update an user" in {
        val request = FakeRequest("POST", "/api/v0/user/user3")
          .withJsonBody(Json.parse("""{"name": "new name"}"""))
          .withHeaders("user" → "user2", "X-Organisation" → "default")

        val result = userCtrl.update("user3")(request)
        status(result) must beEqualTo(200).updateMessage(s ⇒ s"$s\n${contentAsString(result)}")

        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.name must_=== "new name"
      }

      "lock an user" in {
        val authRequest1 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user3", "password": "my-secret-password"}"""))
        val authResult1 = authenticationCtrl.login(authRequest1)
        status(authResult1) must_=== 200

        val request = FakeRequest("POST", "/api/v0/user/user3")
          .withJsonBody(Json.parse("""{"status": "Locked"}"""))
          .withHeaders("user" → "user2", "X-Organisation" → "default")

        val result = userCtrl.update("user3")(request)
        status(result) must_=== 200
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Locked"

        // then authentication must fail
        val authRequest2 = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(Json.parse("""{"user": "user3", "password": "my-secret-password"}"""))
        val authResult2 = authenticationCtrl.login(authRequest2)
        status(authResult2) must_=== 401
      }

      "unlock an user" in {
        val keyAuthRequest = FakeRequest("GET", "/api/v0/user/current")
          .withHeaders("Authorization" → "Bearer azertyazerty")

        authenticateSrv.getAuthContext(keyAuthRequest) must beFailedTry

        val request = FakeRequest("POST", "/api/v0/user/user4")
          .withJsonBody(Json.parse("""{"status": "Ok"}"""))
          .withHeaders("user" → "user2", "X-Organisation" → "cert")

        val result = userCtrl.update("user4")(request)
        status(result) must beEqualTo(200).updateMessage(s ⇒ s"$s\n${contentAsString(result)}")
        val resultUser = contentAsJson(result).as[OutputUser]
        resultUser.status must_=== "Ok"

        authenticateSrv.getAuthContext(keyAuthRequest) must beSuccessfulTry
      }
    }
  }
}
