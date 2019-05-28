package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.OutputTask
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class TaskCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val taskCtrl: TaskCtrl              = app.instanceOf[TaskCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] task controller" should {

      "list available tasks and get one task" in {
        val requestList = FakeRequest("GET", "/api/case/task").withHeaders("user" → "user1")
        val resultList  = taskCtrl.list(requestList)

        status(resultList) shouldEqual 200

        val list = contentAsJson(resultList).as[Seq[OutputTask]]

        list.length shouldEqual 2

        val t1 = list.find(_.title == "case 1 task").get
        val request    = FakeRequest("GET", s"/api/case/task/${t1.id}").withHeaders("user" → "user1")
        val result     = taskCtrl.get(t1.id)(request)
        val resultTask = contentAsJson(result)

        status(result) shouldEqual 200

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = Json.toJson(
          OutputTask(
            _id = resultTaskOutput._id,
            id = resultTaskOutput.id,
            title = "case 1 task",
            description = Some("description task 1"),
            startDate = None,
            flag = false,
            status = "waiting",
            order = resultTaskOutput.order,
            group = Some("group1"),
            endDate = None,
            dueDate = None
          )
        )

        resultTask.toString shouldEqual expected.toString
      }

      "create a new task for an existing case" in {
        val request = FakeRequest("POST", "/api/case/#1/task?flag=true")
          .withJsonBody(
            Json
              .parse(
                """{
                    "title": "case 1 task",
                    "group": "group1",
                    "description": "description task 1",
                    "status": "waiting"
                }"""
              )
          )
          .withHeaders("user" → "user1")

        val result     = taskCtrl.create("#1")(request)
        val resultTask = contentAsJson(result)

        status(result) shouldEqual 201

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = Json.toJson(
          OutputTask(
            _id = resultTaskOutput._id,
            id = resultTaskOutput.id,
            title = "case 1 task",
            description = Some("description task 1"),
            startDate = None,
            flag = true,
            status = "waiting",
            order = 0,
            group = Some("group1"),
            endDate = None,
            dueDate = None
          )
        )

        resultTask.toString shouldEqual expected.toString

        val requestGet = FakeRequest("GET", s"/api/case/task/${resultTaskOutput.id}").withHeaders("user" → "user1")
        val resultGet  = taskCtrl.get(resultTaskOutput.id)(requestGet)

        status(resultGet) shouldEqual 200
      }
    }
  }
}
