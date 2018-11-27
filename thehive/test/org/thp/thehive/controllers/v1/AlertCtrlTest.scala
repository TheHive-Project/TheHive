package org.thp.thehive.controllers.v1

import scala.concurrent.Future

import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}

import akka.stream.Materializer
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.controllers.Authenticated
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models._

class AlertCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv                 = DummyUserSrv(permissions = Seq(Permissions.read, Permissions.write))
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
    val alertCtrl: AlertCtrl            = app.instanceOf[AlertCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] alert controller" should {

      "create a new alert" in {
        val request = FakeRequest("POST", "/api/v1/alert")
          .withJsonBody(Json.toJson(InputAlert("test", "source1", "sourceRef1", "new alert", "test alert")))
        val result       = alertCtrl.create(request)
        val createdAlert = contentAsJson(result).as[OutputAlert]
        val expected = OutputAlert(
          _id = createdAlert._id,
          _createdBy = createdAlert._createdBy,
          _updatedBy = None,
          _createdAt = createdAlert._createdAt,
          _updatedAt = None,
          title = "new alert",
          description = "test alert",
          severity = 2,
          date = createdAlert.date,
          tags = Set.empty,
          flag = false,
          tlp = 2,
          pap = 2,
          status = "new",
          follow = true,
          user = dummyUserSrv.userId,
          customFields = Set.empty
        )

        createdAlert must_=== expected
      }

//      "get a alert" in {
//        val now             = new Date()
//        val alertSrv         = app.instanceOf[AlertSrv]
//        val userSrv         = app.instanceOf[UserSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val createdAlert = app.instanceOf[Database].transaction { graph ⇒
//          alertSrv
//            .create(
//              Alert(
//                number = 0,
//                title = "alert title (get alert test)",
//                description = "alert description (get alert test)",
//                severity = 2,
//                startDate = now,
//                endDate = None,
//                tags = Seq("tag1", "tag2"),
//                flag = true,
//                tlp = 1,
//                pap = 3,
//                status = AlertStatus.open,
//                summary = None
//              ),
//              userSrv.getOrFail(dummyUserSrv.authContext.userId)(graph),
//              organisationSrv.getOrFail("default")(graph),
//              Nil
//            )(graph, dummyUserSrv.authContext)
//        }
//        val request  = FakeRequest("GET", s"/api/v1/alert/#${createdAlert.number}")
//        val result   = alertCtrl.get("#" + createdAlert.number)(request)
//        val bodyJson = contentAsJson(result)
//        val expected = Json.obj(
//          "number"      → createdAlert.number,
//          "title"       → "alert title (get alert test)",
//          "description" → "alert description (get alert test)",
//          "severity"    → 2,
//          "startDate"   → now.getTime,
//          "endDate"     → JsNull,
//          "tags"        → Seq("tag1", "tag2"),
//          "flag"        → true,
//          "tlp"         → 1,
//          "pap"         → 3,
//          "status"      → "open",
//          "summary"     → JsNull,
//          "_createdBy"  → dummyUserSrv.authContext.userId,
//          "_id"         → (bodyJson \ "_id").as[String],
//          "_createdAt"  → createdAlert._createdAt,
//          "_updatedAt"  → JsNull,
//          "_updatedBy"  → JsNull
//        )
//
//        bodyJson must be equalTo expected
//      }
//
//      "update a alert" in {
//        val now             = new Date()
//        val alertSrv         = app.instanceOf[AlertSrv]
//        val userSrv         = app.instanceOf[UserSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val db              = app.instanceOf[Database]
//        val createdAlert = db.transaction { graph ⇒
//          alertSrv
//            .create(
//              Alert(
//                number = 0,
//                title = "alert title (update alert test)",
//                description = "alert description (update alert test)",
//                severity = 2,
//                startDate = now,
//                endDate = None,
//                tags = Seq("tag1", "tag2"),
//                flag = true,
//                tlp = 1,
//                pap = 3,
//                status = AlertStatus.open,
//                summary = None
//              ),
//              userSrv.getOrFail(dummyUserSrv.authContext.userId)(graph),
//              organisationSrv.getOrFail("default")(graph),
//              Nil
//            )(graph, dummyUserSrv.authContext)
//        }
//        val request = FakeRequest("PATCH", s"/api/v1/alert/#${createdAlert.number}")
//          .withJsonBody(
//            Json.obj(
//              "title"  → "new title",
//              "flag"   → false,
//              "tlp"    → 2,
//              "pap"    → 1,
//              "status" → "resolved"
//            ))
//        val updateResult = alertCtrl.update("#" + createdAlert.number)(request)
//        status(updateResult) must_=== 204
//        val getResult = alertCtrl.get("#" + createdAlert.number)(request)
//        val bodyJson  = contentAsJson(getResult)
//        val expected = Json.obj(
//          "number"      → createdAlert.number,
//          "title"       → "new title",
//          "description" → "alert description (update alert test)",
//          "severity"    → 2,
//          "startDate"   → now.getTime,
//          "endDate"     → JsNull,
//          "tags"        → Seq("tag1", "tag2"),
//          "flag"        → false,
//          "tlp"         → 2,
//          "pap"         → 1,
//          "status"      → "resolved",
//          "summary"     → JsNull,
//          "_createdBy"  → dummyUserSrv.authContext.userId,
//          "_id"         → (bodyJson \ "_id").as[String],
//          "_createdAt"  → createdAlert._createdAt,
//          "_updatedAt"  → (bodyJson \ "_updatedAt").as[Date],
//          "_updatedBy"  → "test"
//        )
//
//        bodyJson must be equalTo expected
//      }
    }
  }
}
