package org.thp.thehive.controllers.v0

import org.thp.thehive.services.TheHiveOpsNoDeps
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class LogCtrlTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {

  "log controller" should {

    "be able to create a log" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val task = database.roTransaction { implicit graph =>
        taskSrv.startTraversal.has(_.title, "case 1 task 1").getOrFail("Task").get
      }

      val request = FakeRequest("POST", s"/api/case/task/${task._id}/log")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {"message":"log 1\n\n### yeahyeahyeahs", "deleted":false}
            """.stripMargin))
      val result = logCtrl.create(task._id.toString)(request)

      status(result) shouldEqual 201

      database.roTransaction { implicit graph =>
        taskSrv.get(task).logs.has(_.message, "log 1\n\n### yeahyeahyeahs").exists
      } must beTrue
    }

    "be able to create and remove a log" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      val log = database.roTransaction { implicit graph =>
        logSrv.startTraversal.has(_.message, "log for action test").getOrFail("Log").get
      }

      val requestDelete = FakeRequest("DELETE", s"/api/case/task/log/${log._id}").withHeaders("user" -> "certuser@thehive.local")
      val resultDelete  = logCtrl.delete(log._id.toString)(requestDelete)

      status(resultDelete) shouldEqual 204

      val deletedLog = database.roTransaction { implicit graph =>
        logSrv.startTraversal.has(_.message, "log for action test").headOption
      }
      deletedLog should beNone
    }
  }
}
