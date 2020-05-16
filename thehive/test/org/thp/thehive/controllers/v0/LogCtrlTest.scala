package org.thp.thehive.controllers.v0

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services.{LogSrv, TaskSrv}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class LogCtrlTest extends PlaySpecification with TestAppBuilder {

  "log controller" should {

    "be able to create a log" in testApp { app =>
      val task = app[Database].roTransaction { implicit graph =>
        app[TaskSrv].initSteps.has("title", "case 1 task 1").headOption().get
      }

      val request = FakeRequest("POST", s"/api/case/task/${task._id}/log")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {"message":"log 1\n\n### yeahyeahyeahs", "deleted":false}
            """.stripMargin))
      val result = app[LogCtrl].create(task._id)(request)

      status(result) shouldEqual 201

      app[Database].roTransaction { implicit graph =>
        app[TaskSrv].get(task).logs.has("message", "log 1\n\n### yeahyeahyeahs").exists()
      } must beTrue
    }

    "be able to create and remove a log" in testApp { app =>
      val log = app[Database].roTransaction { implicit graph =>
        app[LogSrv].initSteps.has("message", "log for action test").getOrFail("Log").get
      }

      val requestDelete = FakeRequest("DELETE", s"/api/case/task/log/${log._id}").withHeaders("user" -> "certuser@thehive.local")
      val resultDelete  = app[LogCtrl].delete(log._id)(requestDelete)

      status(resultDelete) shouldEqual 204

      val deletedLog = app[Database].roTransaction { implicit graph =>
        app[LogSrv].initSteps.has("message", "log for action test").headOption()
      }
      deletedLog should beNone
    }
  }
}
