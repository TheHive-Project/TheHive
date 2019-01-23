package org.thp.thehive.controllers.v1

import java.util.Date

import scala.concurrent.Future

import play.api.libs.json.{JsString, Json}
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}

import akka.stream.Materializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.dto.v1.{InputCase, OutputCase, OutputCustomFieldValue}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv, UserSrv}

class CaseCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read, Permissions.write), organisation = "cert")
  val authenticated: Authenticated = mock[Authenticated]
  authenticated.getContext(any[RequestHeader]) returns Future.successful(dummyUserSrv.authContext)
  implicit val ee: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
      .bindToProvider(dbProvider)
      .bindInstance[Authenticated](authenticated)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    DatabaseBuilder.build(app.instanceOf[TheHiveSchema])(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseCtrl: CaseCtrl              = app.instanceOf[CaseCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] case controller" should {

      "create a new case" in {
        val now = new Date()
        val request = FakeRequest("POST", "/api/v1/case")
          .withJsonBody(
            Json.toJson(InputCase(
              title = "case title (create case test)",
              description = "case description (create case test)",
              severity = Some(2),
              startDate = Some(now),
              tags = Seq("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3)
            )))
        val result     = caseCtrl.create(request)
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = OutputCase(
          _id = resultCase._id,
          _createdBy = resultCase._createdBy,
          _updatedBy = None,
          _createdAt = resultCase._createdAt,
          _updatedAt = None,
          number = resultCase.number,
          title = "case title (create case test)",
          description = "case description (create case test)",
          severity = 2,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2"),
          flag = false,
          tlp = 1,
          pap = 3,
          status = "open",
          summary = None,
          user = dummyUserSrv.authContext.userId,
          customFields = Set.empty
        )

        resultCase must_=== expected
      }

      "create a new case using a template" in {
        val now = new Date()
        val request = FakeRequest("POST", "/api/v1/case")
          .withJsonBody(
            Json.toJsObject(InputCase(
              title = "case title (create case test with template)",
              description = "case description (create case test with template)",
              severity = None,
              startDate = Some(now),
              tags = Seq("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3)
            )) + ("caseTemplate" → JsString("spam")))
        val result     = caseCtrl.create(request)
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = OutputCase(
          _id = resultCase._id,
          _createdBy = resultCase._createdBy,
          _updatedBy = None,
          _createdAt = resultCase._createdAt,
          _updatedAt = None,
          number = resultCase.number,
          title = "[SPAM] case title (create case test with template)",
          description = "case description (create case test with template)",
          severity = 1,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2", "spam", "src:mail"),
          flag = false,
          tlp = 1,
          pap = 3,
          status = "open",
          summary = None,
          user = dummyUserSrv.authContext.userId,
          customFields = Set(
            OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", None),
            OutputCustomFieldValue("string1", "string custom field", "string", Some("string1 custom field"))
          )
        )

        resultCase must_=== expected
      }

      "get a case" in {
        val now             = new Date()
        val caseSrv         = app.instanceOf[CaseSrv]
        val userSrv         = app.instanceOf[UserSrv]
        val organisationSrv = app.instanceOf[OrganisationSrv]
        val createdCase = app.instanceOf[Database].transaction { graph ⇒
          caseSrv
            .create(
              Case(
                number = 0,
                title = "case title (get case test)",
                description = "case description (get case test)",
                severity = 2,
                startDate = now,
                endDate = None,
                tags = Seq("tag1", "tag2"),
                flag = true,
                tlp = 1,
                pap = 3,
                status = CaseStatus.open,
                summary = None
              ),
              userSrv.getOrFail(dummyUserSrv.authContext.userId)(graph),
              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
              Map.empty,
              None
            )(graph, dummyUserSrv.authContext)
        }
        val request    = FakeRequest("GET", s"/api/v1/case/#${createdCase.number}")
        val result     = caseCtrl.get("#" + createdCase.number)(request)
        val resultCase = contentAsJson(result).as[OutputCase]
        val expected = OutputCase(
          number = createdCase.number,
          title = "case title (get case test)",
          description = "case description (get case test)",
          severity = 2,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2"),
          flag = true,
          tlp = 1,
          pap = 3,
          status = "open",
          user = dummyUserSrv.authContext.userId,
          _createdBy = dummyUserSrv.authContext.userId,
          _id = createdCase._id,
          _createdAt = createdCase._createdAt,
        )

        resultCase must_=== expected
      }

      "update a case" in {
        val now             = new Date()
        val caseSrv         = app.instanceOf[CaseSrv]
        val userSrv         = app.instanceOf[UserSrv]
        val organisationSrv = app.instanceOf[OrganisationSrv]
        val db              = app.instanceOf[Database]
        val createdCase = db.transaction { graph ⇒
          caseSrv
            .create(
              Case(
                number = 0,
                title = "case title (update case test)",
                description = "case description (update case test)",
                severity = 2,
                startDate = now,
                endDate = None,
                tags = Seq("tag1", "tag2"),
                flag = true,
                tlp = 1,
                pap = 3,
                status = CaseStatus.open,
                summary = None
              ),
              userSrv.getOrFail(dummyUserSrv.authContext.userId)(graph),
              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
              Map.empty,
              None
            )(graph, dummyUserSrv.authContext)
        }
        val updateRequest = FakeRequest("PATCH", s"/api/v1/case/#${createdCase.number}")
          .withJsonBody(
            Json.obj(
              "title"  → "new title",
              "flag"   → false,
              "tlp"    → 2,
              "pap"    → 1,
              "status" → "resolved"
            ))
        val updateResult = caseCtrl.update("#" + createdCase.number)(updateRequest)
        status(updateResult) must_=== 204

        val getRequest = FakeRequest("GET", s"/api/v1/case/#${createdCase.number}")
        val getResult  = caseCtrl.get("#" + createdCase.number)(getRequest)
        val resultCase = contentAsJson(getResult).as[OutputCase]
        val expected = OutputCase(
          number = createdCase.number,
          title = "new title",
          description = "case description (update case test)",
          severity = 2,
          startDate = now,
          endDate = None,
          tags = Set("tag1", "tag2"),
          flag = false,
          tlp = 2,
          pap = 1,
          status = "resolved",
          user = dummyUserSrv.authContext.userId,
          _createdBy = dummyUserSrv.authContext.userId,
          _id = createdCase._id,
          _createdAt = createdCase._createdAt,
          _updatedAt = resultCase._updatedAt,
          _updatedBy = Some(dummyUserSrv.authContext.userId)
        )

        resultCase must_=== expected
      }
    }
  }
}
