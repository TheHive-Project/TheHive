package org.thp.thehive.connector.cortex.services

import akka.actor.Terminated
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.dto.v0.InputAction
import org.thp.thehive.connector.cortex.models.RichAction
import org.thp.thehive.controllers.v0.TheHiveQueryExecutor
import org.thp.thehive.dto.v0.OutputTask
import org.thp.thehive.models.{DatabaseBuilder, EntityHelper, Permissions, TheHiveSchema}
import org.thp.thehive.services.{LocalUserSrv, TaskSrv}
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.concurrent.Future
import scala.concurrent.duration._

class ActionSrvTest extends PlaySpecification with Mockito with FakeCortexClient {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val a = app(dbProvider, 3333)
    specs(dbProvider.name, a, dbProvider) ^ step(teardownDatabase(a)) ^ step(shutdownActorSystem(a))
  }

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def shutdownActorSystem(app: AppBuilder): Future[Terminated] = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder, db: DatabaseProvider): Fragment = {

    def tasksList(theHiveQueryExecutor: TheHiveQueryExecutor): Seq[OutputTask] = {
      val requestList = FakeRequest("GET", "/api/case/task/_search").withHeaders("user" -> "user1")
      val resultList  = theHiveQueryExecutor.task.search(requestList)

      status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")

      contentAsJson(resultList).as[Seq[OutputTask]]
    }

    s"[$name] action service" should {
      implicit lazy val ws: CustomWSAPI          = this.app(db, 3334).instanceOf[CustomWSAPI]
      implicit lazy val auth: Authentication     = KeyAuthentication("test")
      implicit lazy val authContext: AuthContext = dummyUserSrv.authContext

      "execute and create an action" in {
        withCortexClient { client =>
          app.bindInstance(CortexConfig(Map(client.name -> client), 3.seconds, 3))
          setupDatabase(app)

          app.instanceOf[Database].transaction { implicit graph =>
            val taskSrv                                     = app.instanceOf[TaskSrv]
            implicit val entityHelperWrites: Writes[Entity] = app.instanceOf[EntityHelper].writes
            val actionSrv                                   = app.instanceOf[ActionSrv]

            val t1 = tasksList(app.instanceOf[TheHiveQueryExecutor]).find(_.title == "case 1 task 1")

            t1 must beSome

            val task1 = t1.get
            val inputAction = InputAction(
              "respTest1",
              Some("respTest1"),
              Some(client.name),
              "case_task",
              task1.id,
              Some("test"),
              Some(Json.parse("""{"test": true}""").as[JsObject]),
              None
            )

            val r = actionSrv.execute(
              inputAction,
              taskSrv.get(task1.id).getOrFail().get
            )
            val richAction = await(r)

            richAction must beAnInstanceOf[RichAction]
            richAction.responderId shouldEqual "respTest1"

            // TODO test finished method
          }
        }
      }
    }
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def app(dbProvider: DatabaseProvider, port: Int): AppBuilder =
    AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bindInstance[AuthSrv](mock[AuthSrv])
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[CortexActor]("cortex-actor")
      .addConfiguration(
        Configuration(
          "play.modules.disabled"                     -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule"),
          "akka.remote.netty.tcp.port"                -> port,
          "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on",
          "akka.actor.provider"                       -> "cluster"
        )
      )
}
