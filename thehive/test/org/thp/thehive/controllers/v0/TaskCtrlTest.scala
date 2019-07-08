package org.thp.thehive.controllers.v0

import java.util.Date

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

import io.scalaland.chimney.dsl._
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.OutputTask
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv

case class TestTask(
    title: String,
    group: Option[String] = None,
    description: Option[String] = None,
    owner: Option[String] = None,
    status: String,
    flag: Boolean = false,
    startDate: Option[Date] = None,
    endDate: Option[Date] = None,
    order: Int = 0,
    dueDate: Option[Date] = None
)

object TestTask {

  def apply(outputTask: OutputTask): TestTask =
    outputTask.into[TestTask].transform
}

class TaskCtrlTest extends PlaySpecification with Mockito {
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
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val taskCtrl: TaskCtrl   = app.instanceOf[TaskCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    def tasksList: Seq[OutputTask] = {
      val requestList = FakeRequest("GET", "/api/case/task/_search").withHeaders("user" -> "user1")
      val resultList  = theHiveQueryExecutor.task.search(requestList)

      status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")

      contentAsJson(resultList).as[Seq[OutputTask]]
    }

    s"[$name] task controller" should {

      "list available tasks and get one task" in {
        val t1 = tasksList.find(_.title == "case 1 task 1")
        t1 should beSome.setMessage("Task 1 not found")

        val task1      = t1.get
        val request    = FakeRequest("GET", s"/api/case/task/${task1.id}").withHeaders("user" -> "user1")
        val result     = taskCtrl.get(task1.id)(request)
        val resultTask = contentAsJson(result)

        status(result) shouldEqual 200

        val expected = TestTask(
          title = "case 1 task 1",
          description = Some("description task 1"),
          owner = Some("user1"),
          startDate = None,
          status = "Waiting",
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        TestTask(resultTask.as[OutputTask]) must_=== expected
      }

      "patch a task" in {
        val t2 = tasksList.find(_.title == "case 1 task 2")
        t2 should beSome.setMessage("Task 2 not found")

        val task2 = t2.get
        val request = FakeRequest("PATCH", s"/api/case/task/${task2.id}")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""{"title": "new title task 2", "owner": "user1", "status": "InProgress"}"""))
        val result = taskCtrl.update(task2.id)(request)

        status(result) shouldEqual 204

        val expected = TestTask(
          title = "new title task 2",
          description = Some("description task 2"),
          owner = Some("user1"),
          startDate = None,
          flag = true,
          status = "InProgress",
          order = 1,
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        val newList = tasksList
        val newTask = newList.find(_.title == "new title task 2").map(TestTask.apply)
        newTask must beSome(expected)
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
          .withHeaders("user" -> "user1")

        val result     = taskCtrl.create("#1")(request)
        val resultTask = contentAsJson(result)
        status(result) shouldEqual 201

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = TestTask(
          title = "case 1 task",
          description = Some("description task 1"),
          owner = None, // FIXME
          startDate = None,
          flag = true,
          status = "Waiting",
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        TestTask(resultTaskOutput) must_=== expected

        val requestGet = FakeRequest("GET", s"/api/case/task/${resultTaskOutput.id}").withHeaders("user" -> "user1")
        val resultGet  = taskCtrl.get(resultTaskOutput.id)(requestGet)

        status(resultGet) shouldEqual 200
      }

      "unset task owner" in {
        val t3 = tasksList.find(_.title == "case 3 task 1")
        t3 should beSome.setMessage("Task 3 not found")

        val task3 = t3.get
        val request = FakeRequest("PATCH", s"/api/case/task/${task3.id}")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""{"owner": null}"""))
        val result = taskCtrl.update(task3.id)(request)

        status(result) shouldEqual 204

        val newList = tasksList
        val newTask = newList.find(_.title == "case 3 task 1").map(TestTask.apply)

        val expected = TestTask(
          title = "case 3 task 1",
          description = Some("description task 3"),
          owner = None,
          startDate = None,
          flag = true,
          status = "Waiting",
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        newTask must beSome(expected)

      }
    }
  }
}
