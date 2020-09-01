package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.test.PlaySpecification

class TaskAssignedTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certadmin@thehive.local").authContext

  "task assigned trigger" should {
    "be properly triggered on task assignment" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          task1 <- app[TaskSrv].startTraversal.has("title", "case 1 task 1").getOrFail("Task")
          user1 <- app[UserSrv].startTraversal.getByName("certuser@thehive.local").getOrFail("User")
          user2 <- app[UserSrv].startTraversal.getByName("certadmin@thehive.local").getOrFail("User")
          _     <- app[TaskSrv].assign(task1, user1)
          _     <- app[AuditSrv].flushPendingAudit()
          audit <- app[AuditSrv].startTraversal.has("objectId", task1._id).getOrFail("Audit")
          orga  <- app[OrganisationSrv].get("cert").getOrFail("Organisation")
          taskAssignedTrigger = new TaskAssigned(app[TaskSrv])
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, Some(user1)) must beTrue
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, Some(user2)) must beFalse
        } yield ()
      } must beASuccessfulTry
    }
  }
}
