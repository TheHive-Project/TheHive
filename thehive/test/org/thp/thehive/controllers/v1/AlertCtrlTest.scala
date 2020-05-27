package org.thp.thehive.controllers.v1

import java.util.Date

import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertSrv
import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}

case class TestAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String],
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
      alert.tlp,
      alert.pap,
      alert.read,
      alert.follow
    )
}

class AlertCtrlTest extends PlaySpecification with TestAppBuilder {
  "alert controller" should {

    "create a new alert" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(Json.toJson(InputAlert("test", "source1", "sourceRef1", None, "new alert", "test alert")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[AlertCtrl].create(request)
      status(result) must_=== 201
      val createdAlert = contentAsJson(result).as[OutputAlert]
      val expected = OutputAlert(
        _id = createdAlert._id,
        _type = "Alert",
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
        tlp = 2,
        pap = 2,
        read = false,
        follow = true,
        customFields = Set.empty
      )

      createdAlert must_=== expected
    }

    "fail to create a duplicated alert" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(Json.toJson(InputAlert("testType", "testSource", "ref2", None, "new alert", "test alert")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[AlertCtrl].create(request)
      status(result) must_=== 400
    }

    "create an alert with a case template" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(
          Json.toJsObject(InputAlert("test", "source1", "sourceRef1Template", None, "new alert", "test alert"))
            + ("caseTemplate" -> JsString("spam"))
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[AlertCtrl].create(request)
      status(result) must_=== 201
      val createdAlert = contentAsJson(result).as[OutputAlert]
      val expected = OutputAlert(
        _id = createdAlert._id,
        _type = "Alert",
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
        tlp = 2,
        pap = 2,
        read = false,
        follow = true,
        customFields = Set.empty,
        caseTemplate = Some("spam")
      )

      createdAlert must_=== expected
    }

    "get an alert" in testApp { app =>
      val alertSrv = app.apply[AlertSrv]
      app.apply[Database].roTransaction { implicit graph =>
        alertSrv.initSteps.has("sourceRef", "ref1").getOrFail()
      } must beSuccessfulTry.which { alert: Alert with Entity =>
        val request = FakeRequest("GET", s"/api/v1/alert/${alert._id}").withHeaders("user" -> "socuser@thehive.local")
        val result  = app[AlertCtrl].get(alert._id)(request)
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
          tlp = 2,
          pap = 2,
          read = false,
          follow = true
        )
        TestAlert(resultAlert) must_=== expected
      }
    }

    "update an alert" in testApp { app =>
      pending
    }

    "mark an alert as read" in testApp { app =>
      pending
    }

    "unfollow an alert" in testApp { app =>
      pending
    }

    "create a case from an alert" in testApp { app =>
      pending
    }
  }
}
