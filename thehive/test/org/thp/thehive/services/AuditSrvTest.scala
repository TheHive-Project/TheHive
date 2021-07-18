package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import java.util.Date
import scala.util.Success

class AuditSrvTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").getSystemAuthContext

  "audit service" should {
    "get main audits by ids and sorted" in testApp { app =>
      import app._
      import app.thehiveModule._

      val org = database.roTransaction { implicit graph =>
        organisationSrv.getOrFail(EntityName("cert")).get
      }
      // Create 3 case events first
      val c1 = database.tryTransaction { implicit graph =>
        val c = caseSrv
          .create(
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
            org,
            Seq.empty,
            None,
            Nil,
            Map.empty,
            None,
            None
          )
          .get
        caseSrv.updateTags(c.`case`, Set("lol")).get
        Success(c)
      }.get

      database.tryTransaction { implicit graph =>
        caseSrv.createTask(
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
      database.roTransaction { implicit graph =>
        val audits = auditSrv.startTraversal.toSeq

        val r = auditSrv.getMainByIds(Order.asc, audits.map(_._id): _*).toSeq

        // Only the main ones
        r.head shouldEqual audits.filter(_.mainAction).minBy(_._createdAt)
      }
    }

    "merge audits" in testApp { app =>
      import app._
      import app.thehiveModule._

      val auditedTask = database
        .tryTransaction(implicit graph =>
          taskSrv.create(
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
      database.tryTransaction { implicit graph =>
        auditSrv.mergeAudits(taskSrv.update(taskSrv.get(auditedTask._id), Nil)) {
          case (taskSteps, updatedFields) =>
            taskSteps
              .clone()
              .getOrFail("Task")
              .flatMap(auditSrv.task.update(_, updatedFields))
        }
      } must beSuccessfulTry
    }
  }
}
