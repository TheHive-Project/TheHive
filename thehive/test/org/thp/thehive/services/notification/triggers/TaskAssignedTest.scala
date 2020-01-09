package org.thp.thehive.services.notification.triggers

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services._

class TaskAssignedTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certadmin@thehive.local").authContext

  "task assigned trigger" should {
    "be properly triggered on task assignment" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          task1 <- app[TaskSrv].initSteps.has("title", "case 1 task 1").getOrFail()
          user1 <- app[UserSrv].initSteps.getByName("certuser@thehive.local").getOrFail()
          user2 <- app[UserSrv].initSteps.getByName("certadmin@thehive.local").getOrFail()
          _     <- app[TaskSrv].assign(task1, user1)
          _     <- app[AuditSrv].flushPendingAudit()
          audit <- app[AuditSrv].initSteps.has("objectId", task1._id).getOrFail()
          orga  <- app[OrganisationSrv].get("cert").getOrFail()
          taskAssignedTrigger = new TaskAssigned(app[TaskSrv])
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, user1) must beTrue
          _                   = taskAssignedTrigger.filter(audit, Some(task1), orga, user2) must beFalse
        } yield ()
      } must beASuccessfulTry
    }
  }
}
