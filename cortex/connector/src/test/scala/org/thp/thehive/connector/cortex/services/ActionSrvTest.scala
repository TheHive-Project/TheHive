package org.thp.thehive.connector.cortex.services

import java.util.Date

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.cortex.dto.v0.{CortexJobType, CortexOutputJob}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.controllers.v0.ActionCtrl
import org.thp.thehive.connector.cortex.models.{Action, JobStatus, RichAction}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{LocalUserSrv, TaskSrv}
import play.api.libs.json.Json
import play.api.test.{NoMaterializer, PlaySpecification}
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

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app)) // ^ step(shutdownActorSystem(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

//  def shutdownActorSystem(app: AppBuilder): Future[Terminated] = app.app.actorSystem.terminate()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] action service" should {
      "execute and create an action" in {
        app.instanceOf[Database].roTransaction { implicit graph =>
          val taskSrv    = app.instanceOf[TaskSrv]
          val actionSrv  = app.instanceOf[ActionSrv]
          val actionCtrl = app.instanceOf[ActionCtrl]

          val t1 = taskSrv.initSteps.toList.find(_.title == "case 1 task 1")
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
                                                                        }
                                                                      ]""".stripMargin).toString
        }
      }
    }
}
