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
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}


class CaseCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv = DummyUserSrv(permissions = Permissions.all)
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
    val caseCtrl: CaseCtrl = app.instanceOf[CaseCtrl]
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
          .withJsonBody(Json.toJson(InputCase(
            title = "case title (create case test)",
            description = "case description (create case test)",
            severity = Some(2),
            startDate = Some(now),
            tags = Seq("tag1", "tag2"),
            flag = Some(false),
            tlp = Some(1),
            pap = Some(3),
            customFieldValue = inputCustomFields
          )).as[JsObject] + ("caseTemplate" → JsString("spam")))
          .withHeaders("user" → "user1")

        val result = caseCtrl.create(request)
        val resultCase = contentAsJson(result)
        val resultCaseOutput = resultCase.as[OutputCase]
        val expected = Json.toJson(OutputCase(
          _id = resultCaseOutput._id,
          id = resultCaseOutput.id,
          createdBy = "user1",
          createdAt = resultCaseOutput.createdAt,
          _type = "case",
          caseId = resultCaseOutput.caseId,
          title = "[SPAM] case title (create case test)",
          description = "case description (create case test)",
          severity = 2,
          startDate = now,
          tags = Set("spam", "src:mail", "tag1", "tag2"),
          flag = false,
          tlp = 1,
          pap = 3,
          status = "open",
          owner = None,
          customFields = outputCustomFields,
          stats = Json.obj()
        ))

        resultCase.toString shouldEqual expected.toString
      }

      "try to get a case" in {
        val request = FakeRequest("GET", s"/api/v/case/#1")
          .withHeaders("user" → "user1")
        val result     = caseCtrl.get("#145")(request)

        status(result) shouldEqual 404

        val result2 = caseCtrl.get("#1")(request)
        val resultCase = contentAsJson(result2)
        val resultCaseOutput = resultCase.as[OutputCase]

        val expected = Json.toJson(OutputCase(
          _id = resultCaseOutput._id,
          id = resultCaseOutput.id,
          createdBy = "admin",
          createdAt = resultCaseOutput.createdAt,
          _type = "case",
          caseId = resultCaseOutput.caseId,
          title = "case#1",
          description = "description of case #1",
          severity = 2,
          startDate = new Date(1531667370000L),
          flag = false,
          tlp = 2,
          pap = 2,
          status = "open",
          owner = Some("user1"),
          stats = Json.obj()
        ))

        resultCase.toString shouldEqual expected.toString
      }
    }
  }
}
