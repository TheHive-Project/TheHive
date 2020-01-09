package org.thp.thehive.controllers.v0

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputDashboard
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class DashboardCtrlTest extends PlaySpecification with TestAppBuilder {
  "dashboard controller" should {

    "create a dashboard" in testApp { app =>
      val request = FakeRequest("POST", "/api/dashboard")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""{"title": "title test 1", "description": "desc test 1", "status": "Private", "definition": "def test 1"}"""))
      val result = app[DashboardCtrl].create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val dashboard = contentAsJson(result).as[OutputDashboard]

      dashboard.title shouldEqual "title test 1"
      dashboard.description shouldEqual "desc test 1"
      dashboard.status shouldEqual "Private" pendingUntilFixed "Dashboards rights management ongoing"
      dashboard.definition shouldEqual "def test 1"
    }

    // TODO Add dashboard in test data

//      "get a dashboard if visible" in testApp { app =>
//        val dashboard = createDashboard("title test 2", "desc test 2", "Shared", "def test 2")
//
//        val request = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
//          .withHeaders("user" -> "certuser@thehive.local")
//        val result = app[DashboardCtrl].get(dashboard.id)(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//        contentAsJson(result).as[OutputDashboard].title shouldEqual "title test 2"
//
//        val requestFailed = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
//          .withHeaders("user" -> "user3@thehive.local")
//        val resultFailed = app[DashboardCtrl].get(dashboard.id)(requestFailed)
//
//        status(resultFailed) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
//      }
//
//      "update a dashboard" in testApp { app =>
//        val dashboard = createDashboard("title test 3", "desc test 3", "Shared", "def test 3")
//
//        val request = FakeRequest("PATCH", s"/api/dashboard/${dashboard.id}")
//          .withHeaders("user" -> "certuser@thehive.local")
//          .withJsonBody(Json.parse("""{"title": "updated", "description": "updated", "status": "Private", "definition": "updated"}"""))
//        val result = app[DashboardCtrl].update(dashboard.id)(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//        val updatedDashboard = contentAsJson(result).as[OutputDashboard]
//
//        updatedDashboard.title shouldEqual "updated"
//        updatedDashboard.description shouldEqual "updated"
//        updatedDashboard.status shouldEqual "Private" pendingUntilFixed "Dashboards rights management ongoing"
//        updatedDashboard.definition shouldEqual "updated"
//      }
//
//      "delete a dashboard" in testApp { app =>
//        val dashboard = createDashboard("title test 4", "desc test 4", "Shared", "def test 4")
//
//        val request = FakeRequest("DELETE", s"/api/dashboard/${dashboard.id}")
//          .withHeaders("user" -> "certuser@thehive.local")
//        val result = app[DashboardCtrl].delete(dashboard.id)(request)
//
//        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//        val requestGet = FakeRequest("GET", s"/api/dashboard/${dashboard.id}")
//          .withHeaders("user" -> "certuser@thehive.local")
//        val resultGet = app[DashboardCtrl].get(dashboard.id)(requestGet)
//
//        status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
//      }
//
//      "search a dashboard" in testApp { app =>
//        createDashboard("title test 5", "desc test 5", "Shared", "def test 5")
//        createDashboard("title test 6", "desc test 6", "Private", "def test 6")
//        createDashboard("title test 7", "desc test 7", "Shared", "def test 7")
//        val json = Json.parse("""{
//             "range":"all",
//             "sort":[
//                "-updatedAt",
//                "-createdAt"
//             ],
//             "query":{
//                "_and":[
//                   {
//                      "_not":{
//                         "title":"title test 7"
//                      }
//                   },
//                   {
//                      "_and":[
//                         {
//                            "status":"Private"
//                         },
//                         {
//                            "title":"title test 6"
//                         }
//                      ]
//                   }
//                ]
//             }
//          }""".stripMargin)
//
//        val request = FakeRequest("POST", s"/api/dashboard/_search")
//          .withHeaders("user" -> "certuser@thehive.local")
//          .withJsonBody(json)
//        val result = theHiveQueryExecutor.dashboard.search(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//        contentAsJson(result).as[List[OutputDashboard]].length shouldEqual 1
//      }
  }
}
