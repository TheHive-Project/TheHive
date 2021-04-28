package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{TestAppBuilder => _}
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class ResponderSrvTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  "responder service" should {
    "fetch responders by type" in testApp { app =>
      import app._
      import app.cortexModule._
      import app.thehiveModule._

      val task = database.roTransaction { implicit graph =>
        taskSrv.startTraversal.has(_.title, "case 1 task 1").head
      }

      val r = await(responderSrv.getRespondersByType("case_task", task._id))

      r.find(_._1.name == "respTest1") must beSome
    }

    "search responders" in testApp { app =>
      import app.cortexModule._

      val r = await(responderSrv.searchResponders(Json.obj("query" -> Json.obj())))

      r.size must be greaterThan 0
      r.head._2 shouldEqual Seq("test")
    }
  }
}
