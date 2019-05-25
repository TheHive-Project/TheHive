package org.thp.thehive.controllers.v0

import java.util.Date

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.{InputCase, InputCustomFieldValue, OutputCase, OutputCustomFieldValue}
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

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
    TestCase(
      outputCase.caseId,
      outputCase.title,
      outputCase.description,
      outputCase.severity,
      outputCase.startDate,
      outputCase.endDate,
      outputCase.tags,
      outputCase.flag,
      outputCase.tlp,
      outputCase.pap,
      outputCase.status,
      outputCase.summary,
      outputCase.owner,
      outputCase.customFields,
      outputCase.stats
    )
}

class CaseCtrlTest extends PlaySpecification with Mockito {
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
    val caseCtrl: CaseCtrl              = app.instanceOf[CaseCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] case controller" should {

      "create a new case from spam template" in {
        val now = new Date()

        val outputCustomFields = Set(
          OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", Some("true")),
          OutputCustomFieldValue("string1", "string custom field", "string", Some("string custom field"))
        )
        val inputCustomFields = Seq(
          InputCustomFieldValue("boolean1", Some(true)),
          InputCustomFieldValue("string1", Some("string custom field"))
        )

        val request = FakeRequest("POST", "/api/v0/case")
          .withJsonBody(
            Json
              .toJson(
                InputCase(
                  title = "case title (create case test)",
                  description = "case description (create case test)",
                  severity = Some(2),
                  startDate = Some(now),
                  tags = Seq("tag1", "tag2"),
                  flag = Some(false),
                  tlp = Some(1),
                  pap = Some(3),
                  customFieldValue = inputCustomFields
                )
              )
              .as[JsObject] + ("caseTemplate" → JsString("spam"))
          )
          .withHeaders("user" → "user1")

        val result           = caseCtrl.create(request)
        val resultCase       = contentAsJson(result)
        val resultCaseOutput = resultCase.as[OutputCase]
        val expected = TestCase(
          caseId = resultCaseOutput.caseId,
          title = "[SPAM] case title (create case test)",
          description = "case description (create case test)",
          severity = 2,
          startDate = now,
          endDate = None,
          flag = false,
          tlp = 1,
          pap = 3,
          status = "open",
          tags = Set("spam", "src:mail", "tag1", "tag2"),
          summary = None,
          owner = None,
          customFields = outputCustomFields,
          stats = Json.obj()
        )

        TestCase(resultCaseOutput) shouldEqual expected
      }

      "try to get a case" in {
        val request = FakeRequest("GET", s"/api/v0/case/#2")
          .withHeaders("user" → "user1")
        val result = caseCtrl.get("#145")(request)

        status(result) shouldEqual 404

        val result2          = caseCtrl.get("#2")(request)
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
          status = "open",
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
          .withHeaders("user" → "user1")
          .withJsonBody(
            Json.obj(
              "title"  → "new title",
              "flag"   → false,
              "tlp"    → 2,
              "pap"    → 1,
              "status" → "resolved",
              "tags"   → List("tag1")
            )
          )
        val result = caseCtrl.update("#1")(request)
        status(result) must_=== 200
        val resultCase       = contentAsJson(result)
        val resultCaseOutput = resultCase.as[OutputCase]

        val expected = TestCase(
          caseId = 1,
          title = "new title",
          description = "description of case #1",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          flag = false,
          tlp = 2,
          pap = 1,
          status = "resolved",
          tags = Set("tag1"),
          summary = None,
          owner = Some("user1"),
          customFields = Set.empty,
          stats = Json.obj()
        )

        TestCase(resultCaseOutput) shouldEqual expected
      }

      "get and aggregate properly case stats" in {
        val request = FakeRequest("POST", s"/api/v0/case/_stats")
          .withHeaders("user" → "user1")
          .withJsonBody(
            Json.parse("""{
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
        val result = caseCtrl.stats()(request)
        status(result) must_=== 200
        val resultCase = contentAsJson(result)
        val expected   = Json.parse("""{"tag1":{"count":1},"count":3}""")

        resultCase shouldEqual expected
      }
    }
  }
}
