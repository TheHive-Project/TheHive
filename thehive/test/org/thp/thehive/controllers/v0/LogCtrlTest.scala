package org.thp.thehive.controllers.v0

import scala.util.Try

import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{OutputLog, OutputTask}
import org.thp.thehive.models._

class LogCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val logCtrl: LogCtrl     = app.instanceOf[LogCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    def tasksList: Seq[OutputTask] = {
      val requestList = FakeRequest("GET", "/api/case/task").withHeaders("user" -> "user1")
      val resultList  = theHiveQueryExecutor.task.search(requestList)

      status(resultList) shouldEqual 200

      contentAsJson(resultList).as[Seq[OutputTask]]
    }

    s"[$name] log controller" should {

      "be able to create, retrieve and patch a log" in {
        val task = tasksList.find(_.title == "case 1 task 1").get
        val request = FakeRequest("POST", s"/api/case/task/${task.id}/log")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""
              {"message":"log 1\n\n### yeahyeahyeahs", "deleted":false}
            """.stripMargin))
        val result = logCtrl.create(task.id)(request)

        status(result) shouldEqual 201

        val requestSearch = FakeRequest("POST", s"/api/case/task/log/_search")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse(s"""
              {
                "query":{
                   "_and":[
                      {
                         "_and":[
                            {
                               "_parent":{
                                  "_type":"case_task",
                                  "_query":{
                                     "_id":"${task.id}"
                                  }
                               }
                            },
                            {
                               "_not":{
                                  "status":"Deleted"
                               }
                            }
                         ]
                      }
                   ]
                }
             }
            """.stripMargin))
        val resultSearch = theHiveQueryExecutor.log.search(requestSearch)

        status(resultSearch) shouldEqual 200

        val logJson = contentAsJson(resultSearch)
        val log     = logJson.as[Seq[OutputLog]].head
        val expected = OutputLog(
          _id = log._id,
          id = log.id,
          createdBy = "user1",
          createdAt = log.createdAt,
          _type = "case_task_log",
          message = "log 1\n\n### yeahyeahyeahs",
          startDate = log.createdAt,
          status = "Ok",
          owner = "user1"
        )

        logJson.toString shouldEqual Json.toJson(Seq(expected)).toString

        val requestPatch = FakeRequest("PATCH", s"/api/case/task/log/${log.id}")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse(s"""
              {
                "message":"yeah",
                "deleted": true
             }
            """.stripMargin))
        val resultPatch = logCtrl.update(log.id)(requestPatch)

        status(resultPatch) shouldEqual 204
      }

      "be able to create and remove a log" in {
        val task = tasksList.find(_.title == "case 1 task 1").get

        val requestSearch = FakeRequest("POST", s"/api/case/task/log/_search")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse(s"""
              {
                "query":{
                   "_and":[
                      {
                         "_and":[
                            {
                               "_parent":{
                                  "_type":"case_task",
                                  "_query":{
                                     "_id":"${task.id}"
                                  }
                               }
                            },
                            {
                               "_not":{
                                  "status":"Deleted"
                               }
                            }
                         ]
                      }
                   ]
                }
             }
            """.stripMargin))
        val resultSearch = theHiveQueryExecutor.log.search(requestSearch)

        status(resultSearch) shouldEqual 200

        val logJson = contentAsJson(resultSearch)
        val log     = logJson.as[Seq[OutputLog]].head

        val requestDelete = FakeRequest("DELETE", s"/api/case/task/log/${log.id}").withHeaders("user" -> "user1")
        val resultDelete  = logCtrl.delete(log.id)(requestDelete)

        status(resultDelete) shouldEqual 204

        val resultSearch2 = theHiveQueryExecutor.log.search(requestSearch)

        status(resultSearch2) shouldEqual 200

        val emptyList = contentAsJson(resultSearch2)

        emptyList.as[Seq[OutputLog]].size shouldEqual 0
      }
    }
  }

}
