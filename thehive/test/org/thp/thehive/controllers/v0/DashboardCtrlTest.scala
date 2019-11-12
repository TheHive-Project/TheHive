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

    def createDashboard(title: String, description: String, st: String, definition: String) = {
      val request = FakeRequest("POST", "/api/dashboard")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.parse(s"""{"title": "$title", "description": "$description", "status": "$st", "definition": "$definition"}"""))
      val result = dashboardCtrl.create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[OutputDashboard]
    }

    s"$name dashboard controller" should {

      "create a dashboard" in {
        val dashboard = createDashboard("title test 1", "desc test 1", "Private", "def test 1")

        dashboard.title shouldEqual "title test 1"
        dashboard.description shouldEqual "desc test 1"
        dashboard.status shouldEqual "Private" pendingUntilFixed "Dashboards rights management ongoing"
        dashboard.definition shouldEqual "def test 1"
      }

      "get a dashboard if visible" in {
        val dashboard = createDashboard("title test 2", "desc test 2", "Shared", "def test 2")

        val request = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = dashboardCtrl.get(dashboard.id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[OutputDashboard].title shouldEqual "title test 2"

        val requestFailed = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
        val resultFailed = dashboardCtrl.get(dashboard.id)(requestFailed)

        status(resultFailed) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
      }

      "update a dashboard" in {
        val dashboard = createDashboard("title test 3", "desc test 3", "Shared", "def test 3")

        val request = FakeRequest("PATCH", s"/api/dashboard/${dashboard.id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse("""{"title": "updated", "description": "updated", "status": "Private", "definition": "updated"}"""))
        val result = dashboardCtrl.update(dashboard.id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val updatedDashboard = contentAsJson(result).as[OutputDashboard]

        updatedDashboard.title shouldEqual "updated"
        updatedDashboard.description shouldEqual "updated"
        updatedDashboard.status shouldEqual "Private" pendingUntilFixed "Dashboards rights management ongoing"
        updatedDashboard.definition shouldEqual "updated"
      }

      "delete a dashboard" in {
        val dashboard = createDashboard("title test 4", "desc test 4", "Shared", "def test 4")

        val request = FakeRequest("DELETE", s"/api/dashboard/${dashboard.id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val result = dashboardCtrl.delete(dashboard.id)(request)

        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

        val requestGet = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        val resultGet = dashboardCtrl.get(dashboard.id)(requestGet)

        status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
      }

      "search a dashboard" in {
        createDashboard("title test 5", "desc test 5", "Shared", "def test 5")
        createDashboard("title test 6", "desc test 6", "Private", "def test 6")
        createDashboard("title test 7", "desc test 7", "Shared", "def test 7")
        val json = Json.parse("""{
             "range":"all",
             "sort":[
                "-updatedAt",
                "-createdAt"
             ],
             "query":{
                "_and":[
                   {
                      "_not":{
                         "title":"title test 7"
                      }
                   },
                   {
                      "_and":[
                         {
                            "status":"Private"
                         },
                         {
                            "title":"title test 6"
                         }
                      ]
                   }
                ]
             }
          }""".stripMargin)

        val request = FakeRequest("POST", s"/api/dashboard/_search")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(json)
        val result = theHiveQueryExecutor.dashboard.search(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[List[OutputDashboard]].length shouldEqual 1
      }
    }
  }
}
