package org.thp.thehive.connector.cortex.services

import java.util.Date

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl}
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.controllers.v0.ActionCtrl
import org.thp.thehive.connector.cortex.models.{JobStatus, TheHiveCortexSchemaProvider}
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, CaseSrv, OrganisationSrv, TaskSrv}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.PlaySpecification

import scala.io.Source

class ActionSrvTest extends PlaySpecification with TestAppBuilder {
  val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  override val databaseName: String     = "thehiveCortex"
  override def appConfigure: AppBuilder = super.appConfigure.`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])

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
    "execute, create and handle finished action operations" in testAppBuilder { app =>
      app[Database].roTransaction { implicit graph =>
        val authContextUser1             = AuthContextImpl("certuser@thehive.local", "certuser@thehive.local", "cert", "testRequest", Permissions.all)
        val t1: Option[Task with Entity] = app[TaskSrv].initSteps.has("title", "case 1 task 1").headOption()
        t1 must beSome
        val task1 = t1.get

        val richAction =
          await(app[ActionSrv].execute(task1, None, "respTest1", JsObject.empty)(app[ActionCtrl].entityWrites, authContext))

        richAction.workerId shouldEqual "respTest1"

        val cortexOutputJobOpt = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "AWu78Q1OCVNz03gXK4df")

        cortexOutputJobOpt must beSome

        val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJobOpt.get)(authContext)

        updatedActionTry must beSuccessfulTry

        val updatedAction = updatedActionTry.get

        updatedAction.status must equalTo(JobStatus.Success)
        updatedAction.operations must contain(
          exactly(
            Json.obj("type" -> "AddTagToCase", "status" -> "Success", "message" -> "Success", "tag" -> "mail sent"),
            Json.obj(
              "type"        -> "CreateTask",
              "status"      -> "Success",
              "message"     -> "Success",
              "title"       -> "task created by action",
              "description" -> "yop !"
            ),
            Json.obj(
              "type"    -> "AddCustomFields",
              "status"  -> "Success",
              "message" -> "Success",
              "name"    -> "date1",
              "tpe"     -> "date",
              "value"   -> 1562157321892L
            ),
            Json.obj(
              "type"    -> "AddCustomFields",
              "status"  -> "Success",
              "message" -> "Success",
              "name"    -> "float1",
              "tpe"     -> "float",
              "value"   -> 15.54
            ),
            Json.obj(
              "type"    -> "AddCustomFields",
              "status"  -> "Success",
              "message" -> "Success",
              "name"    -> "boolean1",
              "tpe"     -> "boolean",
              "value"   -> false
            ),
            Json.obj("type" -> "AssignCase", "status" -> "Success", "message" -> "Success", "owner" -> "user2@thehive.local"),
            Json.obj(
              "type"     -> "AddArtifactToCase",
              "status"   -> "Success",
              "message"  -> "Success",
              "data"     -> "testObservable",
              "dataType" -> "mail-subject"
            )
          )
        )
        val relatedCaseTry = app[CaseSrv].get("#1").richCase(authContext).getOrFail()

        relatedCaseTry must beSuccessfulTry

        val relatedCase = relatedCaseTry.get

        app[CaseSrv].get(relatedCase._id).richCase(authContext).getOrFail() must beSuccessfulTry.which(
          richCase => richCase.user must beSome("user2@thehive.local")
        )
//          relatedCase.tags must contain(Tag.fromString("mail sent")) // TODO
        app[CaseSrv].initSteps.tasks(authContextUser1).has("title", "task created by action").toList must contain(
          Task(
            title = "task created by action",
            group = "default",
            description = Some("yop !"),
            status = TaskStatus.Waiting,
            flag = false,
            startDate = None,
            endDate = None,
            order = 0,
            dueDate = None
          )
        )
        relatedCase.customFields.find(_.value.contains(new Date(1562157321892L))) must beSome
        relatedCase.customFields.find(_.value.contains(15.54)) must beSome
        app[CaseSrv].get(relatedCase._id).observables(authContextUser1).toList.find(_.message.contains("test observable from action")) must beSome
      }
    }

    "handle action related to Task and Log" in testAppBuilder { app =>
      app[Database].roTransaction { implicit graph =>
        val authContextUser2 =
          AuthContextImpl("user2@thehive.local", "user2@thehive.local", OrganisationSrv.administration.name, "testRequest", Permissions.all)

        val l1 = app[TaskSrv].initSteps.has("title", "case 4 task 1").logs.headOption()
        l1 must beSome
        val log1 = l1.get

        val richAction = await(app[ActionSrv].execute(log1, None, "respTest1", JsObject.empty)(app[ActionCtrl].entityWrites, authContextUser2))

        val cortexOutputJobOpt = readJsonResource("cortex-jobs.json")
          .as[List[OutputJob]]
          .find(_.id == "FDs5Q1ODXCz03gXK4df")
        cortexOutputJobOpt must beSome

        val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJobOpt.get)(authContextUser2)

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
        val t1Updated = app[TaskSrv].initSteps.toList.find(_.title == "case 4 task 1")
        t1Updated must beSome.which { task =>
          task.status shouldEqual TaskStatus.Completed
          app[TaskSrv].get(task._id).logs.toList.find(_.message == "test log from action") must beSome
        }
      }
    }

    "handle action related to an Alert" in testAppBuilder { app =>
      val alertSrv = app.apply[AlertSrv]
      val authContextUser2 =
        AuthContextImpl("user2@thehive.local", "user2@thehive.local", OrganisationSrv.administration.name, "testRequest", Permissions.all)
      app[Database].roTransaction { implicit graph =>
        alertSrv.initSteps.has("sourceRef", "ref1").getOrFail() must beSuccessfulTry.which { alert =>
          alert.read must beFalse

          val richAction = await(app[ActionSrv].execute(alert, None, "respTest1", JsObject.empty)(app[ActionCtrl].entityWrites, authContextUser2))

          val cortexOutputJob = readJsonResource("cortex-jobs.json")
            .as[List[OutputJob]]
            .find(_.id == "FGv4E3ODXCz03gXK6jk")
          cortexOutputJob must beSome
          val updatedActionTry = app[ActionSrv].finished(richAction._id, cortexOutputJob.get)(authContextUser2)

          updatedActionTry must beSuccessfulTry
        }
      }
      app[Database].roTransaction { implicit graph =>
        alertSrv.initSteps.has("sourceRef", "ref1").getOrFail() must beSuccessfulTry.which { alert =>
          alert.read must beTrue
//            alertSrv.initSteps.get(alert._id).tags.toList must contain(Tag.fromString("test tag from action")) // TODO
        }

        alertSrv.initSteps.has("sourceRef", "ref1").visible(authContext).getOrFail() must beFailedTry
        alertSrv.initSteps.has("sourceRef", "ref1").visible(authContextUser2).getOrFail() must beSuccessfulTry
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
