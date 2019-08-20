package org.thp.thehive.controllers.v0

import java.util.Date

import akka.stream.Materializer
import io.scalaland.chimney.dsl._
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0._
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, LocalUserSrv, TaskSrv}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.Try

case class TestCase(
    caseId: Int,
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    owner: Option[String],
    customFields: Set[OutputCustomFieldValue] = Set.empty,
    stats: JsValue
)

object TestCase {

  def apply(outputCase: OutputCase): TestCase =
    outputCase.into[TestCase].transform
}

class CaseCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseCtrl: CaseCtrl   = app.instanceOf[CaseCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
    val caseSrv: CaseSrv     = app.instanceOf[CaseSrv]
    val taskSrv: TaskSrv     = app.instanceOf[TaskSrv]
    val db: Database         = app.instanceOf[Database]

    s"[$name] case controller" should {

      "create a new case from spam template" in {
        val now = new Date()

        val outputCustomFields = Set(
          OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", Some("true")),
          OutputCustomFieldValue("string1", "string custom field", "string", Some("string1 custom field")),
          OutputCustomFieldValue("date1", "date custom field", "date", Some(now.getTime.toString))
        )
        val inputCustomFields = Seq(
          InputCustomFieldValue("date1", Some(now.getTime)),
          InputCustomFieldValue("boolean1", Some(true))
//          InputCustomFieldValue("string1", Some("string custom field"))
        )

        val request = FakeRequest("POST", "/api/v0/case")
          .withJsonBody(
            Json
              .toJson(
                InputCase(
                  title = "case title (create case test)",
                  description = "case description (create case test)",
                  severity = Some(1),
//                  startDate = Some(now),
                  tags = Set("tag1", "tag2"),
                  flag = Some(false),
                  tlp = Some(1),
                  pap = Some(3),
                  customFieldValue = inputCustomFields
                )
              )
              .as[JsObject] + ("caseTemplate" -> JsString("spam"))
          )
          .withHeaders("user" -> "user1")

        val result = caseCtrl.create(request)
        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultCase       = contentAsJson(result)
        val resultCaseOutput = resultCase.as[OutputCase]
        val expected = TestCase(
          caseId = resultCaseOutput.caseId,
          title = "[SPAM] case title (create case test)",
          description = "case description (create case test)",
          severity = 1,
          startDate = resultCaseOutput.startDate, // FIXME when UI will be fixed
          endDate = None,
          flag = false,
          tlp = 1,
          pap = 3,
          status = "Open",
          tags = Set("spam", "src:mail", "tag1", "tag2"),
          summary = None,
          owner = None,
          customFields = outputCustomFields,
          stats = Json.obj()
        )

        TestCase(resultCaseOutput) shouldEqual expected
      }

      "create a new case from scratch" in {
        val request = FakeRequest("POST", "/api/v0/case")
          .withJsonBody(
            Json
              .parse(
                """{
                     "status":"Open",
                     "severity":1,
                     "tlp":2,
                     "pap":2,
                     "title":"test 6",
                     "description":"desc ok",
                     "tags":[],
                     "tasks":[
                        {
                           "title":"task x",
                           "flag":false,
                           "status":"Waiting"
                        }
                     ]
                  }"""
              )
              .as[JsObject]
          )
          .withHeaders("user" -> "user1")

        val result = caseCtrl.create(request)
        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val outputCase = contentAsJson(result).as[OutputCase]
        TestCase(outputCase) must equalTo(
          TestCase(
            caseId = outputCase.caseId,
            title = "test 6",
            description = "desc ok",
            severity = 1,
            startDate = outputCase.startDate,
            flag = false,
            tlp = 2,
            pap = 2,
            status = "Open",
            tags = Set.empty,
            owner = None,
            stats = JsObject.empty
          )
        )

        val requestList = FakeRequest("GET", "/api/case/task").withHeaders("user" -> "user1")
        val resultList  = theHiveQueryExecutor.task.search(requestList)

        status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")
        val tasksList = contentAsJson(resultList).as[Seq[OutputTask]]
        tasksList.find(_.title == "task x") must beSome
      }

      "try to get a case" in {
        val request = FakeRequest("GET", s"/api/v0/case/#2")
          .withHeaders("user" -> "user1")
        val result = caseCtrl.get("#145")(request)

        status(result) shouldEqual 404

        val result2 = caseCtrl.get("#2")(request)
        status(result2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result2)}")
        val resultCase       = contentAsJson(result2)
        val resultCaseOutput = resultCase.as[OutputCase]

        val expected = TestCase(
          caseId = 2,
          title = "case#2",
          description = "description of case #2",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          flag = false,
          tlp = 2,
          pap = 2,
          status = "Open",
          tags = Set.empty,
          summary = None,
          owner = Some("user2"),
          customFields = Set.empty,
          stats = Json.obj()
        )

        TestCase(resultCaseOutput) must_=== expected
      }

      "update a case properly" in {
        val request = FakeRequest("PATCH", s"/api/v0/case/#1")
          .withHeaders("user" -> "user1")
          .withJsonBody(
            Json.obj(
              "title" -> "new title",
              "flag"  -> true
            )
          )
        val result = caseCtrl.update("#1")(request)
        status(result) must_=== 200
        val resultCase = contentAsJson(result).as[OutputCase]

        resultCase.title must equalTo("new title")
        resultCase.flag must equalTo(true)
      }

      "update a bulk of cases properly" in {
        val request = FakeRequest("PATCH", s"/api/v0/case/_bulk")
          .withHeaders("user" -> "user1")
          .withJsonBody(
            Json.obj(
              "ids"         -> List("#1", "#3"),
              "description" -> "new description",
              "tlp"         -> 1,
              "pap"         -> 1
            )
          )
        val result = caseCtrl.bulkUpdate(request)
        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultCases = contentAsJson(result).as[List[OutputCase]]
        resultCases must have size 2

        resultCases.map(_.description) must contain(be_==("new description")).forall
        resultCases.map(_.tlp) must contain(be_==(1)).forall
        resultCases.map(_.pap) must contain(be_==(1)).forall

        val requestGet1 = FakeRequest("GET", s"/api/v0/case/#1")
          .withHeaders("user" -> "user2")
        val resultGet1 = caseCtrl.get("#1")(requestGet1)
        status(resultGet1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet1)}")
        // Ignore title and flag for case#1 because it can be updated by previous test
        val case1 = contentAsJson(resultGet1).as[OutputCase].copy(title = resultCases.head.title, flag = resultCases.head.flag)

        val requestGet3 = FakeRequest("GET", s"/api/v0/case/#3")
          .withHeaders("user" -> "user2")
        val resultGet3 = caseCtrl.get("#3")(requestGet3)
        status(resultGet3) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet3)}")
        val case3 = contentAsJson(resultGet3).as[OutputCase]

        resultCases.map(TestCase.apply) must contain(exactly(TestCase(case1), TestCase(case3)))
      }

      "search cases" in {
        val request = FakeRequest("POST", s"/api/v0/case/_search?range=0-15&sort=-flag&sort=-startDate&nstats=true")
          .withHeaders("user" -> "user1")
          .withJsonBody(
            Json.parse("""{"query":{"severity":2}}""")
          )
        val result = theHiveQueryExecutor.`case`.search()(request)
        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        header("X-Total", result) must beSome("3")
        val resultCases = contentAsJson(result).as[Seq[OutputCase]]

        resultCases.map(_.caseId) must contain(exactly(1, 2, 3))
      }

      "get and aggregate properly case stats" in {
        val request = FakeRequest("POST", s"/api/v0/case/_stats")
          .withHeaders("user" -> "user3")
          .withJsonBody(
            Json.parse("""{
                            "query": {},
                            "stats":[
                               {
                                  "_agg":"field",
                                  "_field":"tags",
                                  "_select":[
                                     {
                                        "_agg":"count"
                                     }
                                  ],
                                  "_size":1000
                               },
                               {
                                  "_agg":"count"
                               }
                            ]
                         }""")
          )
        val result = theHiveQueryExecutor.`case`.stats()(request)
        status(result) must_=== 200
        val resultCase = contentAsJson(result)

        resultCase("count").as[Int] shouldEqual 2
        (resultCase \ "t1" \ "count").get.as[Int] shouldEqual 2
        (resultCase \ "t2" \ "count").get.as[Int] shouldEqual 1
      }

      "assign a case to an user" in {
        val request = FakeRequest("PATCH", s"/api/v0/case/#4")
          .withHeaders("user" -> "user2")
          .withJsonBody(Json.obj("owner" -> "user2"))
        val result = caseCtrl.update("#4")(request)
        status(result) must_=== 200
        val resultCase       = contentAsJson(result)
        val resultCaseOutput = resultCase.as[OutputCase]

        resultCaseOutput.owner should beSome("user2")
      }

      "force delete a case" in {
        val tasks = db.roTransaction { implicit graph =>
          val authContext = mock[AuthContext]
          authContext.organisation returns "default"
          caseSrv.get("#4").tasks(authContext).toList
        }
        tasks must have size 2

        val requestDel = FakeRequest("DELETE", s"/api/v0/case/#4/force")
          .withHeaders("user" -> "user3")
        val resultDel = caseCtrl.realDelete("#4")(requestDel)
        status(resultDel) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(resultDel)}")

        db.roTransaction { implicit graph =>
          caseSrv.get("#4").headOption() must beNone
          tasks.flatMap(task => taskSrv.get(task).headOption()) must beEmpty
        }
      }
    }
  }
}
