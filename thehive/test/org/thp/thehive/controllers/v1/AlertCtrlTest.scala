package org.thp.thehive.controllers.v1

import java.util.Date

import scala.util.Try

import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders, Entity}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertSrv

case class TestAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String],
    flag: Boolean,
    tlp: Int,
    pap: Int,
    read: Boolean,
    follow: Boolean
)

object TestAlert {

  def apply(alert: OutputAlert): TestAlert =
    TestAlert(
      alert.`type`,
      alert.source,
      alert.sourceRef,
      alert.title,
      alert.description,
      alert.severity,
      alert.date,
      alert.tags,
      alert.flag,
      alert.tlp,
      alert.pap,
      alert.read,
      alert.follow
    )
}

class AlertCtrlTest extends PlaySpecification with Mockito {
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], app.instanceOf[UserSrv].getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val alertCtrl: AlertCtrl = app.instanceOf[AlertCtrl]

    s"[$name] alert controller" should {

      "create a new alert" in {
        val request = FakeRequest("POST", "/api/v1/alert")
          .withJsonBody(Json.toJson(InputAlert("test", "source1", "sourceRef1", "new alert", "test alert")))
          .withHeaders("user" -> "user1")
        val result = alertCtrl.create(request)
        status(result) must_=== 201
        val createdAlert = contentAsJson(result).as[OutputAlert]
        val expected = OutputAlert(
          _id = createdAlert._id,
          _createdBy = createdAlert._createdBy,
          _updatedBy = None,
          _createdAt = createdAlert._createdAt,
          _updatedAt = None,
          `type` = "test",
          source = "source1",
          sourceRef = "sourceRef1",
          title = "new alert",
          description = "test alert",
          severity = 2,
          date = createdAlert.date,
          tags = Set.empty,
          flag = false,
          tlp = 2,
          pap = 2,
          read = false,
          follow = true,
          customFields = Set.empty
        )

        createdAlert must_=== expected
      }

      "fail to create a duplicated alert" in {
        val request = FakeRequest("POST", "/api/v1/alert")
          .withJsonBody(Json.toJson(InputAlert("testType", "testSource", "ref1", "new alert", "test alert")))
          .withHeaders("user" -> "user1")
        val result = alertCtrl.create(request)
        status(result) must_=== 400
      }

      "create an alert with a case template" in {
        val request = FakeRequest("POST", "/api/v1/alert")
          .withJsonBody(
            Json.toJsObject(InputAlert("test", "source1", "sourceRef1Template", "new alert", "test alert"))
              + ("caseTemplate" -> JsString("spam"))
          )
          .withHeaders("user" -> "user1")
        val result = alertCtrl.create(request)
        status(result) must_=== 201
        val createdAlert = contentAsJson(result).as[OutputAlert]
        val expected = OutputAlert(
          _id = createdAlert._id,
          _createdBy = createdAlert._createdBy,
          _updatedBy = None,
          _createdAt = createdAlert._createdAt,
          _updatedAt = None,
          `type` = "test",
          source = "source1",
          sourceRef = "sourceRef1Template",
          title = "new alert",
          description = "test alert",
          severity = 2,
          date = createdAlert.date,
          tags = Set.empty,
          flag = false,
          tlp = 2,
          pap = 2,
          read = false,
          follow = true,
          customFields = Set.empty,
          caseTemplate = Some("spam")
        )

        createdAlert must_=== expected
      }

      "get an alert" in {
        val alertSrv = app.instanceOf[AlertSrv]
        app.instanceOf[Database].roTransaction { implicit graph =>
          alertSrv.initSteps.has(Key("sourceRef"), P.eq("ref1")).getOrFail()
        } must beSuccessfulTry.which { alert: Alert with Entity =>
          val request = FakeRequest("GET", s"/api/v1/alert/${alert._id}").withHeaders("user" -> "user2")
          val result  = alertCtrl.get(alert._id)(request)
          status(result) must_=== 200
          val resultAlert = contentAsJson(result).as[OutputAlert]
          val expected = TestAlert(
            `type` = "testType",
            source = "testSource",
            sourceRef = "ref1",
            title = "alert#1",
            description = "description of alert #1",
            severity = 2,
            date = new Date(1555359572000L),
            tags = Set("test", "alert"),
            flag = false,
            tlp = 2,
            pap = 2,
            read = false,
            follow = true
          )
          TestAlert(resultAlert) must_=== expected
        }
      }

      "update an alert" in {
//        val now             = new Date()
//        val alertSrv        = app.instanceOf[AlertSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val createdAlert = app.instanceOf[Database].transaction { graph ⇒
//          alertSrv
//            .create(
//              Alert(
//                `type` = "test",
//                source = "source1",
//                sourceRef = "sourceRef3",
//                title = "alert title",
//                description = "alert description",
//                severity = 2,
//                date = now,
//                lastSyncDate = now,
//                tags = Seq("test", "alert", "patch"),
//                flag = false,
//                tlp = 2,
//                pap = 2,
//                read = false,
//                follow = true
//              ),
//              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
//              Map.empty,
//              None
//            )(graph, dummyUserSrv.authContext)
//        }
//
//        val request = FakeRequest("PATCH", s"/api/v1/alert/${createdAlert._id}")
//          .withJsonBody(
//            Json.obj(
//              "title" → "new title",
//              "flag"  → false,
//              "tlp"   → 3,
//              "pap"   → 1,
//            ))
//        val updateResult = alertCtrl.update(createdAlert._id)(request)
//        status(updateResult) must_=== 204
//        val getResult   = alertCtrl.get(createdAlert._id)(request)
//        val resultAlert = contentAsJson(getResult).as[OutputAlert]
//        val expected = OutputAlert(
//          _id = resultAlert._id,
//          _createdBy = dummyUserSrv.authContext.userId,
//          _createdAt = resultAlert._createdAt,
//          _updatedBy = Some(dummyUserSrv.authContext.userId),
//          _updatedAt = resultAlert._updatedAt,
//          `type` = "test",
//          source = "source1",
//          sourceRef = "sourceRef3",
//          title = "new title",
//          description = "alert description",
//          severity = 2,
//          date = now,
//          tags = Set("test", "alert", "patch"),
//          flag = false,
//          tlp = 3,
//          pap = 1,
//          status = "New",
//          follow = true
//        )
//
//        resultAlert must be equalTo expected
        pending
      }

      "mark an alert as read" in {
//        val now             = new Date()
//        val alertSrv        = app.instanceOf[AlertSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val createdAlert = app.instanceOf[Database].transaction { graph ⇒
//          alertSrv
//            .create(
//              Alert(
//                `type` = "test",
//                source = "source1",
//                sourceRef = "sourceRef4",
//                title = "alert title",
//                description = "alert description",
//                severity = 2,
//                date = now,
//                lastSyncDate = now,
//                tags = Seq("test", "alert", "patch"),
//                flag = false,
//                tlp = 2,
//                pap = 2,
//                read = false,
//                follow = true
//              ),
//              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
//              Map.empty,
//              None
//            )(graph, dummyUserSrv.authContext)
//        }
//
//        val request          = FakeRequest("POST", s"/api/v1/alert/${createdAlert._id}/read")
//        val markAsReadResult = alertCtrl.markAsRead(createdAlert._id)(request)
//        status(markAsReadResult) must_=== 204
//        val getResult   = alertCtrl.get(createdAlert._id)(request)
//        val resultAlert = contentAsJson(getResult).as[OutputAlert]
//        val expected = OutputAlert(
//          _id = resultAlert._id,
//          _createdBy = dummyUserSrv.authContext.userId,
//          _createdAt = resultAlert._createdAt,
//          _updatedBy = Some(dummyUserSrv.authContext.userId),
//          _updatedAt = resultAlert._updatedAt,
//          `type` = "test",
//          source = "source1",
//          sourceRef = "sourceRef4",
//          title = "alert title",
//          description = "alert description",
//          severity = 2,
//          date = now,
//          tags = Set("test", "alert", "patch"),
//          flag = false,
//          tlp = 2,
//          pap = 2,
//          status = "Ignored",
//          follow = true
//        )
//
//        resultAlert must be equalTo expected
        pending
      }

      "unfollow an alert" in {
//        val now             = new Date()
//        val alertSrv        = app.instanceOf[AlertSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val createdAlert = app.instanceOf[Database].transaction { graph ⇒
//          alertSrv
//            .create(
//              Alert(
//                `type` = "test",
//                source = "source1",
//                sourceRef = "sourceRef5",
//                title = "alert title",
//                description = "alert description",
//                severity = 2,
//                date = now,
//                lastSyncDate = now,
//                tags = Seq("test", "alert", "patch"),
//                flag = false,
//                tlp = 2,
//                pap = 2,
//                read = false,
//                follow = true
//              ),
//              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
//              Map.empty,
//              None
//            )(graph, dummyUserSrv.authContext)
//        }
//
//        val request        = FakeRequest("POST", s"/api/v1/alert/${createdAlert._id}/unfollow")
//        val unfollowResult = alertCtrl.unfollowAlert(createdAlert._id)(request)
//        status(unfollowResult) must_=== 204
//        val getResult   = alertCtrl.get(createdAlert._id)(request)
//        val resultAlert = contentAsJson(getResult).as[OutputAlert]
//        val expected = OutputAlert(
//          _id = resultAlert._id,
//          _createdBy = dummyUserSrv.authContext.userId,
//          _createdAt = resultAlert._createdAt,
//          _updatedBy = Some(dummyUserSrv.authContext.userId),
//          _updatedAt = resultAlert._updatedAt,
//          `type` = "test",
//          source = "source1",
//          sourceRef = "sourceRef5",
//          title = "alert title",
//          description = "alert description",
//          severity = 2,
//          date = now,
//          tags = Set("test", "alert", "patch"),
//          flag = false,
//          tlp = 2,
//          pap = 2,
//          status = "New",
//          follow = true
//        )
//
//        resultAlert must be equalTo expected
        pending
      }

      "create a case from an alert" in {
//        val now             = new Date()
//        val alertSrv        = app.instanceOf[AlertSrv]
//        val organisationSrv = app.instanceOf[OrganisationSrv]
//        val createdAlert = app.instanceOf[Database].transaction { graph ⇒
//          val spamCaseTemplate = app
//            .instanceOf[CaseTemplateSrv]
//            .get("spam")(graph)
//            .richCaseTemplate
//            .getOrFail()
//          alertSrv
//            .create(
//              Alert(
//                `type` = "test",
//                source = "source1",
//                sourceRef = "sourceRef6",
//                title = "alert title",
//                description = "alert description",
//                severity = 2,
//                date = now,
//                lastSyncDate = now,
//                tags = Seq("test", "alert", "create"),
//                flag = false,
//                tlp = 2,
//                pap = 2,
//                read = false,
//                follow = true
//              ),
//              organisationSrv.getOrFail(dummyUserSrv.authContext.organisation)(graph),
//              Map("date1" → Some(now), "string1" → Some("value from alert")),
//              Some(spamCaseTemplate)
//            )(graph, dummyUserSrv.authContext)
//        }
//
//        val request          = FakeRequest("POST", s"/api/v1/alert/${createdAlert._id}/case")
//        val createCaseResult = alertCtrl.createCase(createdAlert._id)(request)
//        status(createCaseResult) must_=== 201
//        val createdCase = contentAsJson(createCaseResult).as[OutputCase]
//        val expected = OutputCase(
//          _id = createdCase._id,
//          _createdBy = dummyUserSrv.authContext.userId,
//          _createdAt = createdCase._createdAt,
//          number = createdCase.number,
//          title = "[SPAM] alert title",
//          description = "alert description",
//          severity = 2,
//          startDate = createdCase.startDate,
//          tags = Set("test", "alert", "create"),
//          flag = false,
//          tlp = 2,
//          pap = 2,
//          status = "open",
//          user = Some(dummyUserSrv.authContext.userId),
//          customFields = Set(
//            OutputCustomFieldValue("date1", "date custom field", "date", Some(now.toString)), // from alert creation
//            OutputCustomFieldValue("string1", "string custom field", "string", Some("value from alert")), // from alert creation
//            OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", None) // from case template
//          )
//        )
//
//        createdCase must be equalTo expected
        pending
      }
    }
  }
}
