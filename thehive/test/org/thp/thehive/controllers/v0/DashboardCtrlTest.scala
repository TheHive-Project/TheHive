package org.thp.thehive.controllers.v0

import org.thp.thehive.dto.v0.OutputDashboard
import org.thp.thehive.services.TheHiveOpsNoDeps
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class DashboardCtrlTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  "dashboard controller" should {

    "create a dashboard" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/dashboard")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""{"title": "title test 1", "description": "desc test 1", "status": "Private", "definition": "{\"items\":[]}"}"""))
      val result = dashboardCtrl.create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val dashboard = contentAsJson(result).as[OutputDashboard]

      dashboard.title shouldEqual "title test 1"
      dashboard.description shouldEqual "desc test 1"
      dashboard.status shouldEqual "Private" pendingUntilFixed "Dashboards rights management ongoing"
      dashboard.definition shouldEqual "{\"items\":[]}"
    }

    "get a dashboard if visible" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val dashboard = database.roTransaction { implicit graph =>
        dashboardSrv.startTraversal.has(_.title, "dashboard cert").getOrFail("Dashboard").get
      }

      val request = FakeRequest("GET", s"/api/dashboard/${dashboard._id}")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = dashboardCtrl.get(dashboard._id.toString)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val requestFailed = FakeRequest("GET", s"/api/dashboard/${dashboard._id}")
        .withHeaders("user" -> "socuser@thehive.local")
      val resultFailed = dashboardCtrl.get(dashboard._id.toString)(requestFailed)

      status(resultFailed) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
    }

    "update a dashboard" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val dashboard = database.roTransaction { implicit graph =>
        dashboardSrv.startTraversal.has(_.title, "dashboard cert").getOrFail("Dashboard").get
      }

      val request = FakeRequest("PATCH", s"/api/dashboard/${dashboard._id}")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(Json.parse("""{"title": "updated", "description": "updated", "status": "Private", "definition": "{}"}"""))
      val result = dashboardCtrl.update(dashboard._id.toString)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val updatedDashboard = contentAsJson(result).as[OutputDashboard]

      updatedDashboard.title shouldEqual "updated"
      updatedDashboard.description shouldEqual "updated"
      updatedDashboard.status shouldEqual "Private"
      updatedDashboard.definition shouldEqual "{}"
    }

    "delete a dashboard" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val dashboard = database.roTransaction { implicit graph =>
        dashboardSrv.startTraversal.has(_.title, "dashboard cert").getOrFail("Dashboard").get
      }

      val request = FakeRequest("DELETE", s"/api/dashboard/${dashboard._id}")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = dashboardCtrl.delete(dashboard._id.toString)(request)

      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      database.roTransaction { implicit graph =>
        dashboardSrv.startTraversal.has(_.title, "dashboard cert").exists must beFalse
      }
    }
  }
}
