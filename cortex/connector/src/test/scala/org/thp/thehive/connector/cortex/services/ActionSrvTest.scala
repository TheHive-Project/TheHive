package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.cortex.dto.v0.CortexOutputJob
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContextImpl, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.controllers.v0.ActionCtrl
import org.thp.thehive.connector.cortex.models.{Action, JobStatus, RichAction}
import org.thp.thehive.controllers.v0.LogCtrl
import org.thp.thehive.dto.v0.OutputLog
import org.thp.thehive.models.{DatabaseBuilder, _}
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.io.Source
import scala.util.Try

class ActionSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] action service" should {
      val taskSrv    = app.instanceOf[TaskSrv]
      val actionSrv  = app.instanceOf[ActionSrv]
      val actionCtrl = app.instanceOf[ActionCtrl]
      val caseSrv    = app.instanceOf[CaseSrv]

      "execute, create and handle finished action operations" in {
        app.instanceOf[Database].roTransaction { implicit graph =>
          val authContextUser1 = AuthContextImpl("user1", "user1", "cert", "testRequest", Permissions.all)
          val t1               = taskSrv.initSteps.toList.find(_.title == "case 1 task 1")
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
            operations = None
          )

          val richAction = await(actionSrv.execute(inputAction, task1)(actionCtrl.entityWrites, dummyUserSrv.authContext))

          richAction must beAnInstanceOf[RichAction]
          richAction.responderId shouldEqual "respTest1"

          val cortexOutputJobOpt = {
            val dataSource = Source.fromResource("cortex-jobs.json")
            val data       = dataSource.mkString
            dataSource.close()
            Json.parse(data).as[List[CortexOutputJob]].find(_.id == "AWu78Q1OCVNz03gXK4df")
          }

          cortexOutputJobOpt must beSome

          val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJobOpt.get)(dummyUserSrv.authContext)

          updatedActionTry must beSuccessfulTry

          val updatedAction = updatedActionTry.get

          updatedAction.status shouldEqual JobStatus.Success
          updatedAction.operations must beSome
          updatedAction.operations.get shouldEqual Json.parse("""[
                                                                        {
                                                                          "tag": "mail sent",
                                                                          "status": "Success",
                                                                          "message": "Success"
                                                                        },
                                                                        {
                                                                          "title": "task created by action",
                                                                          "description": "yop !",
                                                                          "status": "Success",
                                                                          "message": "Success"
                                                                        },
                                                                        {
                                                                          "name": "date1",
                                                                          "tpe": "date",
                                                                          "value": "1562157321892",
                                                                          "message": "Success",
                                                                          "status": "Success"
                                                                        },
                                                                        {
                                                                          "name": "float1",
                                                                          "tpe": "float",
                                                                          "value": "15.54",
                                                                          "message": "Success",
                                                                          "status": "Success"
                                                                        },
                                                                        {
                                                                          "name": "boolean1",
                                                                          "tpe": "boolean",
                                                                          "value": "false",
                                                                          "message": "Success",
                                                                          "status": "Success"
                                                                        },
                                                                        {
                                                                          "data": "testObservable",
                                                                          "dataType": "mail_subject",
                                                                          "dataMessage": "test observable from action",
                                                                          "status": "Success",
                                                                          "message": "Success"
                                                                        },
                                                                        {
                                                                          "owner": "user2",
                                                                          "status": "Success",
                                                                          "message": "Success"
                                                                        }
                                                                      ]""".stripMargin).toString
          val relatedCaseTry = caseSrv.initSteps.get("#1").richCase.getOrFail()

          relatedCaseTry must beSuccessfulTry

          val relatedCase = relatedCaseTry.get

          caseSrv.initSteps.get(relatedCase._id).richCase.getOrFail() must beSuccessfulTry.which(richCase => richCase.user must beSome("user2"))
          relatedCase.tags must contain(Tag("mail sent"))
          caseSrv.initSteps.tasks(authContextUser1).toList.filter(_.title == "task created by action") must contain(
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
        app.instanceOf[Database].roTransaction { implicit graph =>
          val logSrv           = app.instanceOf[LogSrv]
          val authContextUser2 = AuthContextImpl("user2", "user2", "default", "testRequest", Permissions.all)

          val t1 = taskSrv.initSteps.toList.find(_.title == "case 4 task 1")
          t1 must beSome
          val task1   = t1.get
          val log1Try = logSrv.get(getTaskLog(task1._id, app)._id).getOrFail()

          log1Try must beSuccessfulTry

          val log1 = log1Try.get
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
            operations = None
          )

          val richAction = await(actionSrv.execute(inputAction, log1)(actionCtrl.entityWrites, authContextUser2))

          richAction must beAnInstanceOf[RichAction]

          val cortexOutputJobOpt = {
            val dataSource = Source.fromResource("cortex-jobs.json")
            val data       = dataSource.mkString
            dataSource.close()
            Json.parse(data).as[List[CortexOutputJob]].find(_.id == "FDs5Q1ODXCz03gXK4df")
          }

          cortexOutputJobOpt must beSome

          val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJobOpt.get)(authContextUser2)

          updatedActionTry must beSuccessfulTry

          val updatedAction = updatedActionTry.get

          updatedAction.status shouldEqual JobStatus.Success
          updatedAction.operations must beSome
        }
        app.instanceOf[Database].roTransaction { implicit graph =>
          val t1Updated = taskSrv.initSteps.toList.find(_.title == "case 4 task 1")
          t1Updated must beSome.which { task =>
            task.status shouldEqual TaskStatus.Completed
            taskSrv.get(task._id).logs.toList.find(_.message == "test log from action") must beSome
          }
        }
      }

      "handle action related to an Alert" in {
        val alertSrv = app.instanceOf[AlertSrv]
        app.instanceOf[Database].roTransaction { implicit graph =>
          alertSrv.initSteps.has(Key("sourceRef"), P.eq("ref1")).getOrFail() must beSuccessfulTry.which { alert: Alert with Entity =>
            alert.read must beFalse

            val authContextUser2 = AuthContextImpl("user2", "user2", "default", "testRequest", Permissions.all)

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
              operations = None
            )

            val richAction = await(actionSrv.execute(inputAction, alert)(actionCtrl.entityWrites, authContextUser2))

            richAction must beAnInstanceOf[RichAction]

            {
              val dataSource = Source.fromResource("cortex-jobs.json")
              val data       = dataSource.mkString
              dataSource.close()
              Json.parse(data).as[List[CortexOutputJob]].find(_.id == "FGv4E3ODXCz03gXK6jk")
            } must beSome.which { cortexOutputJob: CortexOutputJob =>
              val updatedActionTry = actionSrv.finished(richAction._id, cortexOutputJob)(authContextUser2)

              updatedActionTry must beSuccessfulTry
            }
          }
        }
        app.instanceOf[Database].roTransaction { implicit graph =>
          alertSrv.initSteps.has(Key("sourceRef"), P.eq("ref1")).getOrFail() must beSuccessfulTry.which { alert: Alert with Entity =>
            alert.read must beTrue
            alertSrv.initSteps.get(alert._id).tags.toList must contain(Tag("test tag from action"))
          }
        }
      }
    }

  def getTaskLog(id: String, app: AppBuilder): OutputLog = {
    val logCtrl = app.instanceOf[LogCtrl]
    val request = FakeRequest("POST", s"/api/case/task/$id/log")
      .withHeaders("user" -> "user2", "X-Organisation" -> "default")
      .withJsonBody(Json.parse("""
              {"message":"log for action test", "deleted":false}
            """.stripMargin))
    val result = logCtrl.create(id)(request)

    status(result) shouldEqual 201

    contentAsJson(result).as[OutputLog]
  }
}
