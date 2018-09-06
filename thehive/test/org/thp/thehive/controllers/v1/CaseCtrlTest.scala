package org.thp.thehive.controllers.v1

import java.util.Date

import akka.stream.Materializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.core.Fragments
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models._
import org.thp.thehive.services.{AuditSrv, CaseSrv, OrganisationSrv, UserSrv}
import play.api.libs.json.{JsNull, JsNumber, JsString, Json}
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}

import scala.concurrent.Future

class CaseCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read, Permissions.write))
  val authenticated: Authenticated = mock[Authenticated]
  authenticated.getContext(any[RequestHeader]) returns Future.successful(dummyUserSrv.authContext)
  implicit val ee: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

  Fragments.foreach(DatabaseProviders.list) { dbProvider ⇒
    s"[${dbProvider.name}] case controller" should {

      val app: AppBuilder = AppBuilder()
        .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
        .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
        .bindToProvider(dbProvider)
        .bindToProvider(dbProvider.asHookable)
        .bindInstance[Authenticated](authenticated)
      app.instanceOf[DatabaseBuilder]
      app.instanceOf[AuditSrv]
      val caseCtrl: CaseCtrl = app.instanceOf[CaseCtrl]

      implicit lazy val mat: Materializer = app.instanceOf[Materializer]

      "create a new case" in {
        val now = new Date()
        val request = FakeRequest("POST", "/api/v1/case")
          .withJsonBody(
            Json.obj(
              "title"       → "case title (create case test)",
              "description" → "case description (create case test)",
              "severity"    → 2,
              "startDate"   → Json.toJson(now),
              "tags"        → Seq("tag1", "tag2"),
              "flag"        → false,
              "tlp"         → 1,
              "pap"         → 3
            ))
        val result   = caseCtrl.create(request)
        val bodyJson = contentAsJson(result)
        val expected = Json.obj(
          "number"      → (bodyJson \ "number").as[JsNumber],
          "title"       → "case title (create case test)",
          "description" → "case description (create case test)",
          "severity"    → 2,
          "startDate"   → Json.toJson(now),
          "endDate"     → JsNull,
          "tags"        → Seq("tag1", "tag2"),
          "flag"        → false,
          "tlp"         → 1,
          "pap"         → 3,
          "status"      → "open",
          "summary"     → JsNull,
          "_createdBy"  → dummyUserSrv.authContext.userId,
          "_id"         → (bodyJson \ "_id").as[JsString],
          "_createdAt"  → (bodyJson \ "_createdAt").as[JsNumber],
          "_updatedAt"  → JsNull,
          "_updatedBy"  → JsNull
        )

        bodyJson must be equalTo expected
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
              organisationSrv.getOrFail("default")(graph),
              Nil
            )(graph, dummyUserSrv.authContext)
        }
        val request  = FakeRequest("GET", s"/api/v1/case/#${createdCase.number}")
        val result   = caseCtrl.get("#" + createdCase.number)(request)
        val bodyJson = contentAsJson(result)
        val expected = Json.obj(
          "number"      → createdCase.number,
          "title"       → "case title (get case test)",
          "description" → "case description (get case test)",
          "severity"    → 2,
          "startDate"   → now.getTime,
          "endDate"     → JsNull,
          "tags"        → Seq("tag1", "tag2"),
          "flag"        → true,
          "tlp"         → 1,
          "pap"         → 3,
          "status"      → "open",
          "summary"     → JsNull,
          "_createdBy"  → dummyUserSrv.authContext.userId,
          "_id"         → (bodyJson \ "_id").as[String],
          "_createdAt"  → createdCase._createdAt,
          "_updatedAt"  → JsNull,
          "_updatedBy"  → JsNull
        )

        bodyJson must be equalTo expected
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
              organisationSrv.getOrFail("default")(graph),
              Nil
            )(graph, dummyUserSrv.authContext)
        }
        val request = FakeRequest("PATCH", s"/api/v1/case/#${createdCase.number}")
          .withJsonBody(
            Json.obj(
              "title"  → "new title",
              "flag"   → false,
              "tlp"    → 2,
              "pap"    → 1,
              "status" → "resolved"
            ))
        val updateResult = caseCtrl.update("#" + createdCase.number)(request)
        status(updateResult) must_=== 204
        val getResult = caseCtrl.get("#" + createdCase.number)(request)
        val bodyJson  = contentAsJson(getResult)
        val expected = Json.obj(
          "number"      → createdCase.number,
          "title"       → "new title",
          "description" → "case description (update case test)",
          "severity"    → 2,
          "startDate"   → now.getTime,
          "endDate"     → JsNull,
          "tags"        → Seq("tag1", "tag2"),
          "flag"        → false,
          "tlp"         → 2,
          "pap"         → 1,
          "status"      → "resolved",
          "summary"     → JsNull,
          "_createdBy"  → dummyUserSrv.authContext.userId,
          "_id"         → (bodyJson \ "_id").as[String],
          "_createdAt"  → createdCase._createdAt,
          "_updatedAt"  → (bodyJson \ "_updatedAt").as[Date],
          "_updatedBy"  → "test"
        )

        bodyJson must be equalTo expected
      }
    }
  }
}
