package org.thp.thehive.controllers.v1

import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date
import eu.timepit.refined.auto._

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

class AlertCtrlTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  "alert controller" should {

    "create a new alert" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(Json.toJson(InputAlert("test", "source1", "sourceRef1", None, "new alert", "test alert")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = alertCtrl.create(request)
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
        customFields = Seq.empty,
        caseTemplate = None,
        observableCount = 0L,
        caseId = None,
        extraData = JsObject.empty
      )

      createdAlert must_=== expected
    }

    "fail to create a duplicated alert" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(Json.toJson(InputAlert("testType", "testSource", "ref2", None, "new alert", "test alert")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = alertCtrl.create(request)
      status(result) must_=== 400
    }

    "create an alert with a case template" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/alert")
        .withJsonBody(
          Json.toJsObject(InputAlert("test", "source1", "sourceRef1Template", None, "new alert", "test alert"))
            + ("caseTemplate" -> JsString("spam"))
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = alertCtrl.create(request)
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
        customFields = Seq.empty,
        caseTemplate = Some("spam"),
        observableCount = 0L,
        caseId = None,
        extraData = JsObject.empty
      )

      createdAlert must_=== expected
    }

    "get an alert" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV1._

      database.roTransaction { implicit graph =>
        alertSrv.startTraversal.has(_.sourceRef, "ref1").getOrFail("Alert")
      } must beSuccessfulTry.which { alert: Alert with Entity =>
        val request = FakeRequest("GET", s"/api/v1/alert/${alert._id}").withHeaders("user" -> "socuser@thehive.local")
        val result  = alertCtrl.get(alert._id.toString)(request)
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
          tags = Set("alert", "test"),
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
