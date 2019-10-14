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
import org.thp.scalligraph.steps.StepsOps._
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
          .withJsonBody(Json.toJson(InputAlert("test", "source1", "sourceRef1", None, "new alert", "test alert")))
          .withHeaders("user" -> "user1@thehive.local")
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
          externalLink = None,
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
          .withJsonBody(Json.toJson(InputAlert("testType", "testSource", "ref2", None, "new alert", "test alert")))
          .withHeaders("user" -> "user1@thehive.local")
        val result = alertCtrl.create(request)
        status(result) must_=== 400
      }

      "create an alert with a case template" in {
        val request = FakeRequest("POST", "/api/v1/alert")
          .withJsonBody(
            Json.toJsObject(InputAlert("test", "source1", "sourceRef1Template", None, "new alert", "test alert"))
              + ("caseTemplate" -> JsString("spam"))
          )
          .withHeaders("user" -> "user1@thehive.local")
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
          externalLink = None,
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
          val request = FakeRequest("GET", s"/api/v1/alert/${alert._id}").withHeaders("user" -> "user2@thehive.local")
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
            tags = Set("testNamespace.testPredicate=\"alert\"", "testNamespace.testPredicate=\"test\""),
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
        pending
      }

      "mark an alert as read" in {
        pending
      }

      "unfollow an alert" in {
        pending
      }

      "create a case from an alert" in {
        pending
      }
    }
  }
}
