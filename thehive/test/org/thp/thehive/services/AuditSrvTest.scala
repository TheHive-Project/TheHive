package org.thp.thehive.services

import java.util.Date

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.EntityName
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
        val orgAdmin = app[OrganisationSrv].getOrFail(EntityName("admin")).get
        val c1 = app[Database]
          .tryTransaction(implicit graph =>
            app[CaseSrv].create(
              Case(
                title = "case audit",
                description = "desc audit",
                severity = 1,
                startDate = new Date(),
                endDate = None,
                flag = false,
                tlp = 1,
                pap = 1,
                status = CaseStatus.Open,
                summary = None,
                tags = Nil
              ),
              assignee = None,
              orgAdmin,
              Seq.empty,
              None,
              Nil
            )
          )
          .get
        app[CaseSrv].updateTags(c1.`case`, Set("lol"))
        app[Database].tryTransaction { implicit graph =>
          app[CaseSrv].createTask(
            c1.`case`,
            Task(
              title = "test audit",
              group = "",
              description = None,
              status = TaskStatus.Waiting,
              flag = false,
              startDate = None,
              endDate = None,
              order = 0,
              dueDate = None,
              assignee = None
            )
          )
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
          app[TaskSrv].create(
            Task(
              title = "test audit 1",
              group = "",
              description = None,
              status = TaskStatus.Waiting,
              flag = false,
              startDate = None,
              endDate = None,
              order = 0,
              dueDate = None,
              assignee = None
            ),
            None
          )
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
