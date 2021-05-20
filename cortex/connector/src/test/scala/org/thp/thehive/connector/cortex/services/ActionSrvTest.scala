package org.thp.thehive.connector.cortex.services

import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.connector.cortex.models.JobStatus
import org.thp.thehive.models._
import org.thp.thehive.services.{TheHiveOps, TheHiveOpsNoDeps}
import play.api.libs.json._
import play.api.test.PlaySpecification

import scala.io.Source

class ActionSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  override val databaseName: String = "thehiveCortex"

  "action service" should {
    "execute, create and handle finished action operations" in testApp { app =>
      import app._
      import app.cortexConnector._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        implicit val entityWrites: OWrites[Entity] = actionCtrl.entityWrites
        val task1: Task with Entity                = taskSrv.startTraversal.has(_.title, "case 1 task 1").head

        val richAction = await(actionSrv.execute(task1, None, "respTest1", JsObject.empty))
        richAction.workerId shouldEqual "respTest1"

        val cortexOutputJob = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "AWu78Q1OCVNz03gXK4df")
          .get
        val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJob)
        updatedActionTry must beSuccessfulTry
        val updatedAction = updatedActionTry.get

        updatedAction.status must equalTo(JobStatus.Success)
        updatedAction.operations.map(o => (o \ "type").as[String]) must contain(
          exactly("AddTagToCase", "CreateTask", "AddCustomFields", "AddCustomFields", "AddCustomFields", "AssignCase", "AddArtifactToCase")
        )
        updatedAction.operations.map(o => (o \ "status").as[String]).toSet must beEqualTo(Set("Success")).updateMessage { s =>
          s"$s\nSome operations failed:\n${Json.prettyPrint(JsArray(updatedAction.operations))}"
        }
      }
    }

    "handle action related to Task and Log" in testApp { app =>
      import app._
      import app.cortexConnector._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        implicit val entityWrites: OWrites[Entity] = actionCtrl.entityWrites
        val log1                                   = logSrv.startTraversal.has(_.message, "log for action test").head

        val richAction = await(actionSrv.execute(log1, None, "respTest1", JsObject.empty))
        richAction.workerId shouldEqual "respTest1"

        val cortexOutputJob = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "FDs5Q1ODXCz03gXK4df")
          .get

        val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJob)
        updatedActionTry must beSuccessfulTry
        val updatedAction = updatedActionTry.get

        updatedAction.status shouldEqual JobStatus.Success
        updatedAction.operations must contain(
          exactly(
            Json.obj("message" -> "Success", "type" -> "AddLogToTask", "content" -> "test log from action", "status" -> "Success"),
            Json.obj("message" -> "Success", "type" -> "CloseTask", "status"     -> "Success")
          )
        )
      }

      database.roTransaction { implicit graph =>
        taskSrv.startTraversal.has(_.title, "case 2 task 2").has(_.status, TaskStatus.Completed).exists         must beTrue
        taskSrv.startTraversal.has(_.title, "case 2 task 2").logs.has(_.message, "test log from action").exists must beTrue
      }
    }

    "handle action related to an Alert" in testApp { app =>
      import app._
      import app.cortexConnector._
      import app.thehiveModule._

      TheHiveOps(organisationSrv, customFieldSrv) { ops =>
        import ops.AlertOpsDefs

        implicit val entityWrites: OWrites[Entity] = actionCtrl.entityWrites
        val alert = database.roTransaction { implicit graph =>
          alertSrv.get(EntityName("testType;testSource;ref2")).visible.head
        }

        alert.read must beFalse
        val richAction = await(actionSrv.execute(alert, None, "respTest1", JsObject.empty))

        val cortexOutputJob = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "FGv4E3ODXCz03gXK6jk")
          .get
        val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJob)
        updatedActionTry must beSuccessfulTry

        database.roTransaction { implicit graph =>
          val updatedAlert = alertSrv.get(EntityName("testType;testSource;ref2")).visible.richAlert.head // FIXME
          updatedAlert.read must beTrue
          updatedAlert.tags must contain("test tag from action") // TODO
        }
      }
    }
  }

  def readJsonResource(resourceName: String): JsValue = {
    val dataSource = Source.fromResource(resourceName)
    try {
      val data = dataSource.mkString
      Json.parse(data)
    } finally dataSource.close()
  }
}
