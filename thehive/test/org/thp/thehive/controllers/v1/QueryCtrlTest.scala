package org.thp.thehive.controllers.v1

import org.specs2.mock.Mockito
import org.specs2.specification.core.Fragments
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.models.{DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models.{AppBuilder, DatabaseBuilder, InitialAuthContext, Permissions}
import play.api.libs.json._
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}
import scala.concurrent.Future

import org.thp.scalligraph.auth.AuthSrv

class QueryCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read))
  val authenticated: Authenticated = mock[Authenticated]
  authenticated.getContext(any[RequestHeader]) returns Future.successful(dummyUserSrv.authContext)

  Fragments.foreach(DatabaseProviders.list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
      .bindToProvider(dbProvider)
      .bindInstance[Authenticated](authenticated)
      .bindInstance[AuthSrv](mock[AuthSrv])
    app.instanceOf[DatabaseBuilder]
    val queryCtrl: QueryCtrl = app.instanceOf[QueryCtrl]

    s"[${dbProvider.name}] query controller" should {
      "execute complex query" in {
        val request = FakeRequest("POST", "/api/v1/query")
          .withJsonBody(
            Json.obj("query" → Json.arr(
              Json.obj("_name" → "listCase"),
              Json.obj(
                "_name" → "filter",
                "_and" → Json
                  .arr(Json.obj("_is" → Json.obj("tlp" → 3)), Json.obj("_is" → Json.obj("severity" → 3)), Json.obj("_is" → Json.obj("pap" → 3)))),
              Json.obj("_name" → "toList")
            )))
        val result              = queryCtrl.execute(request)
        val bodyJson            = contentAsJson(result)
        val _id: JsValue        = (bodyJson \ 0 \ "_id").asOpt[JsString].getOrElse(JsNull)
        val _createdAt: JsValue = (bodyJson \ 0 \ "_createdAt").asOpt[JsNumber].getOrElse(JsNull)
        val expected = Json.arr(
          Json.obj(
            "number"      → 4,
            "title"       → "case#4",
            "description" → "description of case #4",
            "severity"    → 3,
            "startDate"   → 1531667370000L,
            "endDate"     → JsNull,
            "tags"        → JsArray(),
            "flag"        → false,
            "tlp"         → 3,
            "pap"         → 3,
            "status"      → "open",
            "summary"     → JsNull,
            "_id"         → _id,
            "_createdAt"  → _createdAt,
            "_createdBy"  → "test",
            "_updatedAt"  → JsNull,
            "_updatedBy"  → JsNull
          ))
        bodyJson must be equalTo expected
      }
    }
  }
}
