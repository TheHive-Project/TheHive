package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.test.PlaySpecification

class TaskAssignedTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certadmin@thehive.local").authContext

  "task assigned trigger" should {
    "be properly triggered on task assignment" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          task1 <- taskSrv.startTraversal.has(_.title, "case 1 task 1").getOrFail("Task")
          user1 <- userSrv.startTraversal.getByName("certuser@thehive.local").getOrFail("User")
          user2 <- userSrv.startTraversal.getByName("certadmin@thehive.local").getOrFail("User")
          _     <- taskSrv.assign(task1, user1)
          _     <- auditSrv.flushPendingAudit()
          audit <- auditSrv.startTraversal.has(_.objectId, task1._id.toString).getOrFail("Audit")
          orga  <- organisationSrv.get(EntityName("cert")).getOrFail("Organisation")
          taskAssignedTrigger = new TaskAssigned(taskSrv)
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, Some(user1)) must beTrue
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, Some(user2)) must beFalse
        } yield ()
      } must beASuccessfulTry
    }
  }
}
