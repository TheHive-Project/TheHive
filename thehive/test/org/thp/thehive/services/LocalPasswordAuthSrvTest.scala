package org.thp.thehive.services

import scala.util.Try

import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}

class LocalPasswordAuthSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val localPasswordAuthProvider = app.instanceOf[LocalPasswordAuthProvider]
    val userSrv                   = app.instanceOf[UserSrv]
    val db                        = app.instanceOf[Database]
    val conf                      = app.instanceOf[Configuration]

    s"[$name] localPasswordAuth service" should {
      "be able to verify passwords" in db.roTransaction { implicit graph =>
        val user3                = userSrv.getOrFail("user3@thehive.local").get
        val localPasswordAuthSrv = localPasswordAuthProvider.apply(conf).get.asInstanceOf[LocalPasswordAuthSrv]
        val request = FakeRequest("POST", "/api/v0/login")
          .withHeaders("X-Organisation" -> "default")
          .withJsonBody(
            Json.parse("""{"user": "user3@thehive.local", "password": "secret"}""")
          )

        localPasswordAuthSrv.authenticate(user3.login, "secret", None)(request) must beSuccessfulTry
      }
    }
  }
}
