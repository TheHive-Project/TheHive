package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.thehive.services.TaskOps._
import play.api.test.PlaySpecification

class TaskSrvTest extends PlaySpecification with TestAppBuilder {
  "task service" should {

    "set actionRequired for an organisation" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext

        val task                 = taskSrv.startTraversal.has(_.title, "taskActionRequired1").getOrFail("Task").get
        val cert                 = organisationSrv.startTraversal.has(_.name, "cert").getOrFail("Organisation").get
        def getActionRequired    = taskSrv.get(task).actionRequired.toSeq.head
        def getActionRequiredMap = taskSrv.get(task).actionRequiredMap.toSeq.toMap

        getActionRequired    must beEqualTo(false)
        getActionRequiredMap must beEqualTo(Map("soc" -> false, "cert" -> false))

        taskSrv.actionRequired(task, cert, actionRequired = true)

        getActionRequired    must beEqualTo(true)
        getActionRequiredMap must beEqualTo(Map("soc" -> false, "cert" -> true))
      }
    }

    "unset actionRequired for an organisation" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext

        val task                 = taskSrv.startTraversal.has(_.title, "taskActionRequired2").getOrFail("Task").get
        val cert                 = organisationSrv.startTraversal.has(_.name, "cert").getOrFail("Organisation").get
        def getActionRequired    = taskSrv.get(task).actionRequired.toSeq.head
        def getActionRequiredMap = taskSrv.get(task).actionRequiredMap.toSeq.toMap

        getActionRequired    must beEqualTo(true)
        getActionRequiredMap must beEqualTo(Map("soc" -> false, "cert" -> true))

        taskSrv.actionRequired(task, cert, actionRequired = false)

        getActionRequired    must beEqualTo(false)
        getActionRequiredMap must beEqualTo(Map("soc" -> false, "cert" -> false))
      }
    }

  }

}
