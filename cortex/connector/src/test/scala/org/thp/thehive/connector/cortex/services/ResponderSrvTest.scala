package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Permissions
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class ResponderSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  "responder service" should {
    "fetch responders by type" in testApp { app =>
      val t = app[Database].roTransaction { implicit graph =>
        app[TaskSrv].initSteps.has("title", "case 1 task 1").getOrFail()
      }

      t must successfulTry.which { task =>
        val r = await(app[ResponderSrv].getRespondersByType("case_task", task._id))

        r.find(_._1.name == "respTest1") must beSome
      }
    }

    "search responders" in testApp { app =>
      val r = await(app[ResponderSrv].searchResponders(Json.obj("query" -> Json.obj())))

      r.size must be greaterThan 0
      r.head._2 shouldEqual Seq("test")
    }
  }
}
