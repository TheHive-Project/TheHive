package org.thp.thehive.connector.cortex.services

import java.util.Date

import scala.io.Source
import scala.util.Try

import play.api.libs.json.{JsValue, Json}
import play.api.test.{NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContextImpl
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.controllers.v0.ActionCtrl
import org.thp.thehive.connector.cortex.models.{Action, JobStatus, TheHiveCortexSchemaProvider}
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, CaseSrv, TaskSrv}

class ActionSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app = TestAppBuilder(dbProvider)
      .`override`(
        _.bindActor[CortexActor]("cortex-actor")
          .bindToProvider[CortexClient, TestCortexClientProvider]
          .bind[Connector, TestConnector]
          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] action service" should {
      val taskSrv    = app.instanceOf[TaskSrv]
      val actionSrv  = app.instanceOf[ActionSrv]
      val actionCtrl = app.instanceOf[ActionCtrl]
      val caseSrv    = app.instanceOf[CaseSrv]
      val db         = app.instanceOf[Database]

      "execute, create and handle finished action operations" in {
        db.roTransaction { implicit graph =>
          val authContextUser1 = AuthContextImpl("user1@thehive.local", "user1@thehive.local", "cert", "testRequest", Permissions.all)
          val t1               = taskSrv.initSteps.has(Key("title"), P.eq("case 1 task 1")).headOption()
          t1 must beSome
          val task1 = t1.get

          val inputAction = Action(
            responderId = "respTest1",
            responderName = Some("respTest1"),
            responderDefinition = None,
            status = JobStatus.Unknown,
            objectType = "Task",
            objectId = task1._id,
            parameters = Json.obj("test" -> true),
            startDate = new Date(),
            endDate = None,
            report = None,
            cortexId = None,
            cortexJobId = None,
            operations = Nil
          )

          val richAction = await(actionSrv.execute(inputAction, task1)(actionCtrl.entityWrites, dummyUserSrv.authContext))

          richAction.responderId shouldEqual "respTest1"

          val cortexOutputJobOpt = readJsonResource("cortex-jobs.json")
            .as[List[CortexOutputJob]]
            .find(_.id == "AWu78Q1OCVNz03gXK4df")

          cortexOutputJobOpt must beSome

          val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJobOpt.get)(dummyUserSrv.authContext)

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
                "value"   -> "1562157321892"
              ),
              Json.obj(
                "type"    -> "AddCustomFields",
                "status"  -> "Success",
                "message" -> "Success",
                "name"    -> "float1",
                "tpe"     -> "float",
                "value"   -> "15.54"
              ),
              Json.obj(
                "type"    -> "AddCustomFields",
                "status"  -> "Success",
                "message" -> "Success",
                "name"    -> "boolean1",
                "tpe"     -> "boolean",
                "value"   -> "false"
              ),
              Json.obj("type" -> "AssignCase", "status" -> "Success", "message" -> "Success", "owner" -> "user2@thehive.local"),
              Json.obj(
                "type"        -> "AddArtifactToCase",
                "status"      -> "Success",
                "message"     -> "Success",
                "data"        -> "testObservable",
                "dataType"    -> "mail-subject",
                "dataMessage" -> "test observable from action"
              )
            )
          )
          val relatedCaseTry = caseSrv.get("#1").richCase.getOrFail()

          relatedCaseTry must beSuccessfulTry

          val relatedCase = relatedCaseTry.get

          caseSrv.get(relatedCase._id).richCase.getOrFail() must beSuccessfulTry.which(richCase => richCase.user must beSome("user2@thehive.local"))
          relatedCase.tags must contain(Tag("mail sent"))
          caseSrv.initSteps.tasks(authContextUser1).has(Key("title"), P.eq("task created by action")).toList must contain(
            Task(
              title = "task created by action",
              group = None,
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
          relatedCase.customFields.find(_.value.contains(15.54.toFloat)) must beSome
          caseSrv.get(relatedCase._id).observables(authContextUser1).toList.find(_.message.contains("test observable from action")) must beSome
        }
      }

      "handle action related to Task and Log" in {
        db.roTransaction { implicit graph =>
          val authContextUser2 = AuthContextImpl("user2@thehive.local", "user2@thehive.local", "default", "testRequest", Permissions.all)

          val l1 = taskSrv.initSteps.has(Key("title"), P.eq("case 4 task 1")).logs.headOption()
          l1 must beSome
          val log1 = l1.get

          val inputAction = Action(
            responderId = "respTest1",
            responderName = Some("respTest1"),
            responderDefinition = None,
            status = JobStatus.Unknown,
            objectType = "Log",
            objectId = log1._id,
            parameters = Json.obj(),
            startDate = new Date(),
            endDate = None,
            report = None,
            cortexId = None,
            cortexJobId = None,
            operations = Nil
          )

          val richAction = await(actionSrv.execute(inputAction, log1)(actionCtrl.entityWrites, authContextUser2))

          val cortexOutputJobOpt = readJsonResource("cortex-jobs.json")
            .as[List[CortexOutputJob]]
            .find(_.id == "FDs5Q1ODXCz03gXK4df")
          cortexOutputJobOpt must beSome

          val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJobOpt.get)(authContextUser2)

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
        db.roTransaction { implicit graph =>
          val t1Updated = taskSrv.initSteps.toList.find(_.title == "case 4 task 1")
          t1Updated must beSome.which { task =>
            task.status shouldEqual TaskStatus.Completed
            taskSrv.get(task._id).logs.toList.find(_.message == "test log from action") must beSome
          }
        }
      }

      "handle action related to an Alert" in {
        val alertSrv = app.instanceOf[AlertSrv]
        db.roTransaction { implicit graph =>
          alertSrv.initSteps.has(Key("sourceRef"), P.eq("ref1")).getOrFail() must beSuccessfulTry.which { alert =>
            alert.read must beFalse

            val authContextUser2 = AuthContextImpl("user2@thehive.local", "user2@thehive.local", "default", "testRequest", Permissions.all)

            val inputAction = Action(
              responderId = "respTest1",
              responderName = Some("respTest1"),
              responderDefinition = None,
              status = JobStatus.Unknown,
              objectType = "Alert",
              objectId = alert._id,
              parameters = Json.obj(),
              startDate = new Date(),
              endDate = None,
              report = None,
              cortexId = None,
              cortexJobId = None,
              operations = Nil
            )

            val richAction = await(actionSrv.execute(inputAction, alert)(actionCtrl.entityWrites, authContextUser2))

            val cortexOutputJob = readJsonResource("cortex-jobs.json")
              .as[List[CortexOutputJob]]
              .find(_.id == "FGv4E3ODXCz03gXK6jk")
            cortexOutputJob must beSome
            val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJob.get)(authContextUser2)

            updatedActionTry must beSuccessfulTry
          }
        }
        db.roTransaction { implicit graph =>
          alertSrv.initSteps.has(Key("sourceRef"), P.eq("ref1")).getOrFail() must beSuccessfulTry.which { alert =>
            alert.read must beTrue
            alertSrv.initSteps.get(alert._id).tags.toList must contain(Tag("test tag from action"))
          }
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
