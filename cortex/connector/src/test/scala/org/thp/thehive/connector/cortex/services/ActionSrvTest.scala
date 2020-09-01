package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.connector.cortex.controllers.v0.ActionCtrl
import org.thp.thehive.connector.cortex.models.{JobStatus, TheHiveCortexSchemaProvider}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.{AlertSrv, LogSrv, TaskSrv}
import org.thp.thehive.{BasicDatabaseProvider, TestAppBuilder}
import play.api.libs.json._
import play.api.test.PlaySpecification

import scala.io.Source

class ActionSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  override val databaseName: String = "thehiveCortex"
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])
      .`override`(
        _.bindActor[CortexActor]("cortex-actor")
          .bindToProvider[CortexClient, TestCortexClientProvider]
          .bind[Connector, TestConnector]
          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
      )
      .bindNamedToProvider[Database, BasicDatabaseProvider]("with-thehive-cortex-schema")

  def testAppBuilder[A](body: AppBuilder => A): A = testApp { app =>
    body(
      app
        .`override`(
          _.bindActor[CortexActor]("cortex-actor")
            .bindToProvider[CortexClient, TestCortexClientProvider]
            .bind[Connector, TestConnector]
            .bindToProvider[Schema, TheHiveCortexSchemaProvider]
        )
    )
  }

  "action service" should {
    "execute, create and handle finished action operations" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        implicit val entityWrites: OWrites[Entity] = app[ActionCtrl].entityWrites
        val task1: Task with Entity                = app[TaskSrv].startTraversal.has("title", "case 1 task 1").head

        val richAction = await(app[ActionSrv].execute(task1, None, "respTest1", JsObject.empty))
        richAction.workerId shouldEqual "respTest1"

        val cortexOutputJob = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "AWu78Q1OCVNz03gXK4df")
          .get
        val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJob)
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
      app[Database].roTransaction { implicit graph =>
        implicit val entityWrites: OWrites[Entity] = app[ActionCtrl].entityWrites
        val log1                                   = app[LogSrv].startTraversal.has("message", "log for action test").head

        val richAction = await(app[ActionSrv].execute(log1, None, "respTest1", JsObject.empty))
        richAction.workerId shouldEqual "respTest1"

        val cortexOutputJob = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "FDs5Q1ODXCz03gXK4df")
          .get

        val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJob)
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

      app[Database].roTransaction { implicit graph =>
        app[TaskSrv].startTraversal.has("title", "case 2 task 2").has("status", "Completed").exists must beTrue
        app[TaskSrv].startTraversal.has("title", "case 2 task 2").logs.has("message", "test log from action").exists must beTrue
      }
    }

    "handle action related to an Alert" in testApp { app =>
      implicit val entityWrites: OWrites[Entity] = app[ActionCtrl].entityWrites
      val alert = app[Database].roTransaction { implicit graph =>
        app[AlertSrv].get("testType;testSource;ref2").visible.head
      }
      alert.read must beFalse
      val richAction = await(app[ActionSrv].execute(alert, None, "respTest1", JsObject.empty))

      val cortexOutputJob = readJsonResource("cortex-jobs.json")
        .as[List[OutputJob]]
        .find(_.id == "FGv4E3ODXCz03gXK6jk")
        .get
      val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJob)
      updatedActionTry must beSuccessfulTry

      app[Database].roTransaction { implicit graph =>
        val updatedAlert = app[AlertSrv].get("testType;testSource;ref2").visible.richAlert.head // FIXME
        updatedAlert.read must beTrue
        updatedAlert.tags.map(_.toString) must contain("test tag from action") // TODO
      }
    }
  }

  def readJsonResource(resourceName: String): JsValue = {
    val dataSource = Source.fromResource("cortex-jobs.json")
    try {
      val data = dataSource.mkString
      Json.parse(data)
    } finally dataSource.close()
  }
}
