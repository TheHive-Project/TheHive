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
        val t1 = tasksList(taskCtrl).find(_.title == "case 1 task 1")
        t1 should beSome.setMessage("Task 1 not found")

        val task1      = t1.get
        val request    = FakeRequest("GET", s"/api/case/task/${task1.id}").withHeaders("user" → "user1")
        val result     = taskCtrl.get(task1.id)(request)
        val resultTask = contentAsJson(result)

        status(result) shouldEqual 200

        val expected = OutputTask(
          _id = task1.id,
          id = task1.id,
          title = "case 1 task 1",
          description = Some("description task 1"),
          owner = Some("user1"),
          startDate = None,
          flag = false,
          status = "Waiting",
          order = 0,
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        resultTask.as[OutputTask] must_=== expected
      }

      "patch a task" in {
        val t2 = tasksList(taskCtrl).find(_.title == "case 1 task 2")
        t2 should beSome.setMessage("Task 2 not found")

        val task2 = t2.get
        val request = FakeRequest("PATCH", s"/api/case/task/${task2.id}")
          .withHeaders("user" → "user1")
          .withJsonBody(Json.parse("""{"title": "new title task 2", "owner": "user1", "status": "InProgress"}"""))
        val result = taskCtrl.update(task2.id)(request)

        status(result) shouldEqual 204

        val expected =
          OutputTask(
            _id = task2.id,
            id = task2.id,
            title = "new title task 2",
            description = Some("description task 2"),
            owner = Some("user1"),
            startDate = None,
            flag = true,
            status = "inProgress",
            order = 1,
            group = Some("group1"),
            endDate = None,
            dueDate = None
          )

        val newList = tasksList(taskCtrl)
        val newTask = newList.find(_.title == "new title task 2")
        newTask must beSome(expected)
        expected must beSome[OutputTask]
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
                    "status": "Waiting"
                }"""
              )
          )
          .withHeaders("user" → "user1")

        val result     = taskCtrl.create("#1")(request)
        val resultTask = contentAsJson(result)
        status(result) shouldEqual 201

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = OutputTask(
          _id = resultTaskOutput._id,
          id = resultTaskOutput.id,
          title = "case 1 task",
          description = Some("description task 1"),
          owner = None, // FIXME
          startDate = None,
          flag = true,
          status = "waiting",
          order = 0,
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        resultTaskOutput must_=== expected

        val requestGet = FakeRequest("GET", s"/api/case/task/${resultTaskOutput.id}").withHeaders("user" → "user1")
        val resultGet  = taskCtrl.get(resultTaskOutput.id)(requestGet)

        status(resultGet) shouldEqual 200
      }

      "unset task owner" in {
        val t3 = tasksList(taskCtrl).find(_.title == "case 3 task 1")
        t3 should beSome.setMessage("Task 3 not found")

        val task3 = t3.get
        val request = FakeRequest("PATCH", s"/api/case/task/${task3.id}")
          .withHeaders("user" → "user1")
          .withJsonBody(Json.parse("""{"owner": null}"""))
        val result = taskCtrl.update(task3.id)(request)

        status(result) shouldEqual 204

        val newList = tasksList(taskCtrl)
        val newTask = newList.find(_.title == "case 3 task 1")

        val expected =
          OutputTask(
            _id = task3.id,
            id = task3.id,
            title = "case 3 task 1",
            description = Some("description task 3"),
            owner = None,
            startDate = None,
            flag = true,
            status = "Waiting",
            order = 0,
            group = Some("group1"),
            endDate = None,
            dueDate = None
          )

        newTask must beSome(expected)

      }
    }
  }

  def tasksList(taskCtrl: TaskCtrl): Seq[OutputTask] = {
    val requestList = FakeRequest("GET", "/api/case/task").withHeaders("user" → "user1")
    val resultList  = taskCtrl.list(requestList)

    status(resultList) shouldEqual 200

    contentAsJson(resultList).as[Seq[OutputTask]]
  }
}
