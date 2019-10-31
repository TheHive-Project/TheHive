package org.thp.thehive.controllers.v0

import java.util.Date

import scala.util.Try

import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import io.scalaland.chimney.dsl._
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.OutputTask
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv, TaskSrv}

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
  def apply(richTask: RichTask): TestTask = apply(richTask.toOutput)

  def apply(outputTask: OutputTask): TestTask =
    outputTask.into[TestTask].transform
}

class TaskCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val taskCtrl: TaskCtrl   = app.instanceOf[TaskCtrl]
    val taskSrv: TaskSrv     = app.instanceOf[TaskSrv]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
    val db                   = app.instanceOf[Database]
    val caseSrv              = app.instanceOf[CaseSrv]

    def getTaskByTitle(title: String): Try[RichTask] = db.roTransaction { implicit graph =>
      taskSrv.initSteps.has(Key("title"), P.eq(title)).richTask.getOrFail()
    }

    s"[$name] task controller" should {

      "list available tasks and get one task" in {
        val task1      = getTaskByTitle("case 1 task 1").get
        val request    = FakeRequest("GET", s"/api/case/task/${task1._id}").withHeaders("user" -> "user1@thehive.local")
        val result     = taskCtrl.get(task1._id)(request)
        val resultTask = contentAsJson(result)

        status(result) shouldEqual 200

        val expected = TestTask(
          title = "case 1 task 1",
          description = Some("description task 1"),
          owner = Some("user1@thehive.local"),
          startDate = None,
          status = "Waiting",
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        TestTask(resultTask.as[OutputTask]) must_=== expected
      }

      "patch a task" in {
        val task2 = getTaskByTitle("case 4 task 1").get
        val request = FakeRequest("PATCH", s"/api/case/task/${task2._id}")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> OrganisationSrv.administration.name)
          .withJsonBody(Json.parse("""{"title": "new title task 2", "owner": "user1@thehive.local", "status": "InProgress"}"""))
        val result = taskCtrl.update(task2._id)(request)

        status(result) shouldEqual 200

        val expected = TestTask(
          title = "new title task 2",
          description = Some("description task 4"),
          owner = Some("user1@thehive.local"),
          startDate = None,
          flag = true,
          status = "InProgress",
          order = 0,
          group = Some("group1"),
          endDate = None,
          dueDate = None
        )

        val newTask = getTaskByTitle("new title task 2")
          .map(TestTask.apply)
          .map(_.copy(startDate = None))
        newTask must beASuccessfulTry(expected)
      }

      "create a new task for an existing case" in {
        val request = FakeRequest("POST", "/api/case/#4/task?flag=true")
          .withJsonBody(
            Json
              .parse(
                """{
                    "title": "case 4 task",
                    "group": "group1",
                    "description": "description task 1",
                    "status": "Waiting"
                }"""
              )
          )
          .withHeaders("user" -> "user3@thehive.local")

        val result     = taskCtrl.create("#4")(request)
        val resultTask = contentAsJson(result)
        status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

        val resultTaskOutput = resultTask.as[OutputTask]
        val expected = TestTask(
          title = "case 4 task",
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

        val requestGet = FakeRequest("GET", s"/api/case/task/${resultTaskOutput.id}").withHeaders("user" -> "user3@thehive.local")
        val resultGet  = taskCtrl.get(resultTaskOutput.id)(requestGet)

        status(resultGet) shouldEqual 200
      }

      "unset task owner" in {
        val task3 = getTaskByTitle("case 3 task 1").get
        val request = FakeRequest("PATCH", s"/api/case/task/${task3._id}")
          .withHeaders("user" -> "user1@thehive.local")
          .withJsonBody(Json.parse("""{"owner": null}"""))
        val result = taskCtrl.update(task3._id)(request)

        status(result) shouldEqual 200

        val newTask = getTaskByTitle("case 3 task 1").map(TestTask.apply)

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

        newTask must beSuccessfulTry(expected)

      }

      "get tasks stats" in {
        val case1 = db.roTransaction(graph => caseSrv.initSteps(graph).has(Key("title"), P.eq("case#1")).getOrFail())

        case1 must beSuccessfulTry

        val c = case1.get

        val request = FakeRequest("POST", "/api/case/task/_stats")
          .withHeaders("user" -> "user1@thehive.local")
          .withJsonBody(
            Json.parse(
              s"""{
                         "query":{
                            "_and":[
                               {
                                  "_parent":{
                                     "_type":"case",
                                     "_query":{
                                        "_id":"${c._id}"
                                     }
                                  }
                               },
                               {
                                  "_not":{
                                     "status":"Cancel"
                                  }
                               }
                            ]
                         },
                         "stats":[
                            {
                               "_agg":"field",
                               "_field":"status",
                               "_select":[
                                  {
                                     "_agg":"count"
                                  }
                               ]
                            },
                            {
                               "_agg":"count"
                            }
                         ]
                      }""".stripMargin
            )
          )
        val result = db.roTransaction(_ => theHiveQueryExecutor.task.stats(request))

        status(result) must equalTo(200)

        contentAsJson(result) shouldEqual Json.parse("""{"count":2,"Waiting":{"count":2}}""")
      }
    }
  }
}
