package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, CortexClientConfig}
import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestConfigItem
import org.thp.thehive.connector.cortex.{CortexTestConnector, TestAppBuilder}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{TestAppBuilder => _}
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class ResponderSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  override val databaseName: String = "thehiveCortex"

  "responder service" should {
    "fetch responders by type" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.cortexConnector._

      val task = database.roTransaction { implicit graph =>
        taskSrv.startTraversal.has(_.title, "case 1 task 1").head
      }

      val r = await(responderSrv.getRespondersByType("case_task", task._id))

      r.find(_._1.name == "respTest1") must beSome
    }

    "search responders" in testApp { app =>
      import app.cortexConnector._

      val r = await(responderSrv.searchResponders(Json.obj("query" -> Json.obj())))

      r.size must be greaterThan 0
      r.head._2 shouldEqual Seq("test")
    }
  }
}
