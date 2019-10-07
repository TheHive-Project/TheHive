package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{InputShare, ObservablesFilter, TasksFilter}
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class ShareCtrlTest extends PlaySpecification with Mockito {
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
    val shareCtrl: ShareCtrl = app.instanceOf[ShareCtrl]
    val db                   = app.instanceOf[Database]

    "share a case" in {
      val request = FakeRequest("PUT", "/api/case/#4/shares")
        .withJsonBody(
          Json.toJson(
            InputShare("cert", "all", TasksFilter.all, ObservablesFilter.all)
          )
        )
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result = shareCtrl.shareCase("#4")(request)

      status(result) shouldEqual 201
    }
  }

}
