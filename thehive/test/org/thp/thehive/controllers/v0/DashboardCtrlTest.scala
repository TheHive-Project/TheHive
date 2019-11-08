package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputDashboard
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class DashboardCtrlTest extends PlaySpecification with Mockito {
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
    val dashboardCtrl: DashboardCtrl = app.instanceOf[DashboardCtrl]
    val theHiveQueryExecutor         = app.instanceOf[TheHiveQueryExecutor]

    s"$name dashboard controller" should {
//      def createDashboard(title: String, description: String, status: String, definition: String) = {
//        val request = FakeRequest("POST", "/api/dashboard")
//          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
//          .withJsonBody(Json.parse(s"""{"title": "$title", "description": "$description"}, "status": "$status"}, "definition": "$definition"}"""))
//        val result = dashboardCtrl.create(request)
//
//        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//        contentAsJson(result).as[OutputDashboard]
//      }

      "create a dashboard" in {
        val request = FakeRequest("POST", "/api/dashboard")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse(s"""{"title": "test 1", "description": "test desc 1", "status": "Private", "definition": "test def 1"}"""))
        val result = dashboardCtrl.create(request)

        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

        contentAsJson(result).as[OutputDashboard].title shouldEqual "test 1"
      }
    }
  }
}
