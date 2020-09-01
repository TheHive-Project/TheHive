package org.thp.thehive.services

import java.util.Date

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

class AuditSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").getSystemAuthContext

  "audit service" should {
    "get main audits by ids and sorted" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        // Create 3 case events first
        val orgAdmin = app[OrganisationSrv].getOrFail("admin").get
        val c1 = app[Database]
          .tryTransaction(implicit graph =>
            app[CaseSrv].create(
              Case(0, "case audit", "desc audit", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
              None,
              orgAdmin,
              Set.empty,
              Seq.empty,
              None,
              Nil
            )
          )
          .get
        app[CaseSrv].updateTagNames(c1.`case`, Set("lol"))
        app[Database].tryTransaction { implicit graph =>
          val t = app[TaskSrv].create(Task("test audit", "", None, TaskStatus.Waiting, flag = false, None, None, 0, None), None)
          app[ShareSrv].shareTask(t.get, c1.`case`, orgAdmin)
        }
        val audits = app[AuditSrv].startTraversal.toSeq

        val r = app[AuditSrv].getMainByIds(Order.asc, audits.map(_._id): _*).toSeq

        // Only the main ones
        r.head shouldEqual audits.filter(_.mainAction).minBy(_._createdAt)
      }
    }
    "merge audits" in testApp { app =>
      val auditedTask = app[Database]
        .tryTransaction(implicit graph =>
          app[TaskSrv].create(Task("test audit 1", "", None, TaskStatus.Waiting, flag = false, None, None, 0, None), None)
        )
        .get
      app[Database].tryTransaction { implicit graph =>
        app[AuditSrv].mergeAudits(app[TaskSrv].update(app[TaskSrv].get(auditedTask._id), Nil)) {
          case (taskSteps, updatedFields) =>
            taskSteps
              .clone()
              .getOrFail("Task")
              .flatMap(app[AuditSrv].task.update(_, updatedFields))
        }
      } must beSuccessfulTry
    }
  }
}
