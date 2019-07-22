package org.thp.thehive.connector.cortex.services

import java.util.Date

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.{Action, JobStatus, RichAction}
import org.thp.thehive.controllers.v0.{CaseCtrl, TheHiveQueryExecutor}
import org.thp.thehive.dto.v0.{OutputCase, OutputTask}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{CaseSrv, LocalUserSrv, TaskSrv}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.concurrent.duration._

class ActionSrvTest extends PlaySpecification with Mockito with FakeCortexClient {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bindInstance[AuthSrv](mock[AuthSrv])
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[CortexActor]("cortex-actor")
      .addConfiguration(
        Configuration(
          "play.modules.disabled" -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule"),
          "akka.remote.netty.tcp.port" -> 3335,
          "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on",
          "akka.actor.provider" -> "cluster"
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app)) ^ step(shutdownActorSystem(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def shutdownActorSystem(app: AppBuilder): Unit = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder): Fragment = {
    val taskSrv              = app.instanceOf[TaskSrv]
    val caseSrv              = app.instanceOf[CaseSrv]
    val db                   = app.instanceOf[Database]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
    val caseCtrl: CaseCtrl   = app.instanceOf[CaseCtrl]

    def tasksList: Seq[OutputTask] = {
      val requestList = FakeRequest("GET", "/api/case/task/_search").withHeaders("user" -> "user1")
      val resultList  = theHiveQueryExecutor.task.search(requestList)

      status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")

      contentAsJson(resultList).as[Seq[OutputTask]]
    }

    def getCase: OutputCase = {
      val request = FakeRequest("GET", s"/api/v0/case/#2")
        .withHeaders("user" -> "user1")

      val result2 = caseCtrl.get("#2")(request)
      status(result2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result2)}")
      val resultCase = contentAsJson(result2)

      resultCase.as[OutputCase]
    }

    s"[$name] action service" should {
      implicit lazy val ws: CustomWSAPI          = app.instanceOf[CustomWSAPI]
      implicit lazy val auth: Authentication     = KeyAuthentication("test")
      implicit lazy val authContext: AuthContext = dummyUserSrv.authContext

      import org.thp.thehive.models.EntityFormat._

      "execute and create an action" in db.transaction { implicit graph =>
        withCortexClient { client =>
          app.bindInstance(CortexConfig(Map(client.name -> client), 3.seconds, 3))
          val actionSrv = app.instanceOf[ActionSrv]

          val t1          = tasksList.find(_.title == "case 1 task 1").get
          val relatedCase = getCase
          val inputAction = InputAction(
            "Mailer_1_0",
            "Mailer_1_0",
            Some(client.name),
            "case_task",
            t1.id,
            Some("test"),
            Some(Json.parse("""{"test": true}""").as[JsObject]),
            None
          )

          val r = actionSrv.execute(
            inputAction,
            taskSrv.get(t1.id).getOrFail().get,
            Some(caseSrv.get(relatedCase._id).getOrFail().get)
          )

          await(r) must beAnInstanceOf[RichAction]

          pending("Needs a way to inject proper CortexConfig")
        }
      }

      "update an action when job is finished" in db.transaction { implicit graph =>
        withCortexClient { client =>
          val actionSrv = app.instanceOf[ActionSrv]

          val task = taskSrv.get(tasksList.find(_.title == "case 1 task 1").get.id).getOrFail()

          task must beSuccessfulTry

          val taskWithEntity = task.get
          val action = Action(
            "Mailer_1_0",
            Some("Mailer_1_0"),
            Some("test"),
            JobStatus.Waiting,
            taskWithEntity,
            new Date(),
            Some(client.name),
            None
          )
          val richAction = actionSrv.create(action, taskWithEntity)

          richAction shouldEqual 1
        }
      }
    }
  }
}
