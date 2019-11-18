package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputUser
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class AuthenticationCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val authenticationCtrl = app.instanceOf[AuthenticationCtrl]

    "login and logout users" in {
      val request = FakeRequest("POST", "/api/v0/login")
        .withJsonBody(
          Json.parse("""{"user": "user3@thehive.local", "password": "secret"}""")
        )
      val result = authenticationCtrl.login()(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[OutputUser].name shouldEqual "only on admin"

      val requestOut = FakeRequest("GET", "/api/v0/logout")
      val resultOut  = authenticationCtrl.logout()(requestOut)

      status(resultOut) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    }
  }
}
