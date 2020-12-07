package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Permissions, Profile}
import play.api.test.PlaySpecification

class TaskSrvTest extends PlaySpecification with TestAppBuilder {
  "task service" should {

    "set actionRequired for an organisation" in testApp { app =>
      app[Database].transaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc").authContext

        // Setup
        val task = app[TaskSrv].startTraversal.has(_.title, "taskActionRequired1").getOrFail("Task").get
        val soc = app[OrganisationSrv].startTraversal.has(_.name, "soc").getOrFail("Organisation").get
        val cert = app[OrganisationSrv].startTraversal.has(_.name, "cert").getOrFail("Organisation").get
        app[TaskSrv].isActionRequired(task, Seq(soc, cert)) must beEqualTo(Map("soc" -> false, "cert" -> false))

        app[TaskSrv].actionRequired(task, soc, actionRequired = true)

        // Test
        app[TaskSrv].isActionRequired(task, Seq(soc, cert)) must beEqualTo(Map("soc" -> true, "cert" -> false))
      }
    }

    "unset actionRequired for an organisation" in testApp { app =>
      app[Database].transaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc").authContext

        // Setup
        val task = app[TaskSrv].startTraversal.has(_.title, "taskActionRequired2").getOrFail("Task").get
        val soc = app[OrganisationSrv].startTraversal.has(_.name, "soc").getOrFail("Organisation").get
        val cert = app[OrganisationSrv].startTraversal.has(_.name, "cert").getOrFail("Organisation").get
        app[TaskSrv].isActionRequired(task, Seq(soc, cert)) must beEqualTo(Map("soc" -> true, "cert" -> false))

        app[TaskSrv].actionRequired(task, soc, actionRequired = false)

        // Test
        app[TaskSrv].isActionRequired(task, Seq(soc, cert)) must beEqualTo(Map("soc" -> false, "cert" -> false))
      }
    }

  }

}
