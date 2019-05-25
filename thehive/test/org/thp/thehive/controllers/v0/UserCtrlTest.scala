package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class UserCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())
  val authSrv: AuthSrv      = mock[AuthSrv]

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindInstance(authSrv)
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val userCtrl: UserCtrl              = app.instanceOf[UserCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] user controller" should {

      "search users" in {
        val request = FakeRequest("POST", "/api/v0/user/_search?range=all&sort=%2Bname")
          .withJsonBody(Json.parse("""{"query": {"_and": [{"status": "Ok"}]}}"""))
          .withHeaders("user" → "user1")

        val result = userCtrl.search(request)
        status(result) must_=== 200

        val resultUsers = contentAsJson(result)
        val expected =
          Seq(
            OutputUser(id = "user1", login = "user1", name = "Thomas", organisation = "cert", roles = Set("read", "write", "alert")),
            OutputUser(id = "user2", login = "user2", name = "U", organisation = "cert", roles = Set("read"))
          )

        resultUsers.as[Seq[OutputUser]] shouldEqual expected
      }
    }
  }
}
