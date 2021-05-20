package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.dto.v0._
import org.thp.thehive.models.RichObservable
import org.thp.thehive.services.TheHiveOpsNoDeps
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

case class TestAlert(
    `type`: String,
    source: String,
    sourceRef: String,
    title: String,
    description: String,
    severity: Int,
    date: Date,
    tags: Set[String] = Set.empty,
    tlp: Int,
    pap: Int,
    status: String,
    follow: Boolean,
    customFields: JsObject = JsObject.empty,
    caseTemplate: Option[String] = None
)

object TestAlert {

  def apply(outputAlert: OutputAlert): TestAlert =
    outputAlert.into[TestAlert].transform
}

class AlertCtrlTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  "create an alert" in testApp { app =>
    import app.thehiveModuleV0._

    val now                = new Date()
    val outputCustomFields = Json.obj("string1" -> Json.obj("string" -> "string custom field"), "float1" -> Json.obj("float" -> 42.0))
    val inputCustomFields = Seq(
      InputCustomFieldValue("float1", Some(42), None),
      InputCustomFieldValue("string1", Some("string custom field"), None)
    )
    val inputObservables =
      Seq(
        InputObservable(dataType = "ip", data = Seq("127.0.0.1"), message = Some("localhost"), tlp = Some(1), tags = Set("here")),
        InputObservable(
          dataType = "file",
          data = Seq("hello.txt;text/plain;aGVsbG8gd29ybGQgIQ=="),
          message = Some("coucou"),
          tlp = Some(1),
          tags = Set("welcome", "message")
        )
      )
    val outputObservables = Seq(
      TestObservable(dataType = "ip", data = Some("127.0.0.1"), message = Some("localhost"), tlp = 1, tags = Set("here")),
      TestObservable(
        dataType = "file",
        attachment = Some(
          OutputAttachment(
            "hello.txt",
            Seq(
              "a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889",
              "b1fda0e52e8099d2aeb80f57bb91548cace3093f",
              "905138a85e85e74344e90d25dba7299e"
            ),
            13,
            "text/plain",
            "a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889"
          )
        ),
        message = Some("coucou"),
        tlp = 1,
        tags = Set("welcome", "message")
      )
    )
    val request = FakeRequest("POST", "/api/v0/alert")
      .withJsonBody(
        Json
          .toJson(
            InputAlert(
              `type` = "test",
              source = "alert_creation_test",
              sourceRef = "#1",
              externalLink = None,
              title = "alert title (create alert test)",
              description = "alert description (create alert test)",
              severity = Some(2),
              date = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3),
              customFields = inputCustomFields
            )
          )
          .as[JsObject] +
          ("caseTemplate" -> JsString("spam")) +
          ("artifacts"    -> Json.toJson(inputObservables))
      )
      .withHeaders("user" -> "certuser@thehive.local")

    val result = alertCtrl.create(request)
    status(result) should equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
    val resultAlert       = contentAsJson(result)
    val resultAlertOutput = resultAlert.as[OutputAlert]
    val expected = TestAlert(
      `type` = "test",
      source = "alert_creation_test",
      sourceRef = "#1",
      title = "alert title (create alert test)",
      description = "alert description (create alert test)",
      severity = 2,
      date = now,
      tags = Set("tag1", "tag2"),
      tlp = 1,
      pap = 3,
      status = "New",
      follow = true,
      customFields = outputCustomFields,
      caseTemplate = Some("spam")
    )

    TestAlert(resultAlertOutput) shouldEqual expected
    resultAlertOutput.artifacts.map(TestObservable.apply) should containTheSameElementsAs(outputObservables)
  }

  "get an alert" in testApp { app =>
    import app.thehiveModuleV0._

    val request = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref1")
      .withHeaders("user" -> "socuser@thehive.local")
    val result = alertCtrl.get("testType;testSource;ref1")(request)
    status(result) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    val resultAlert       = contentAsJson(result)
    val resultAlertOutput = resultAlert.as[OutputAlert]
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
      status = "New",
      follow = true,
      customFields = Json.obj("integer1" -> Json.obj("integer" -> 42)),
      caseTemplate = Some("spam")
    )

    TestAlert(resultAlertOutput) shouldEqual expected
    resultAlertOutput
      .artifacts
      .map(o => TestObservable(o)) must contain(
      TestObservable(
        "domain",
        Some("h.fr"),
        None,
        1,
        Set("hello"),
        ioc = true,
        sighted = true,
        Some("observable from alert")
      )
    )
  }

  "update an alert" in testApp { app =>
    import app.thehiveModuleV0._

    val request = FakeRequest("PATCH", "/api/v0/alert/testType;testSource;ref2")
      .withJsonBody(
        Json.obj(
          "tlp" -> 3
        )
      )
      .withHeaders("user" -> "certuser@thehive.local")
    val result = alertCtrl.update("testType;testSource;ref2")(request)
    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    val resultAlert       = contentAsJson(result)
    val resultAlertOutput = resultAlert.as[OutputAlert]
    resultAlertOutput.tlp must beEqualTo(3)
  }

  "mark an alert as read/unread" in testApp { app =>
    import app.thehiveModuleV0._

    val request1 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result1 = alertCtrl.get("testType;testSource;ref3")(request1)
    status(result1)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")
    contentAsJson(result1).as[OutputAlert].status must beEqualTo("New")

    val request2 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/markAsRead")
      .withHeaders("user" -> "certuser@thehive.local")
    val result2 = alertCtrl.markAsRead("testType;testSource;ref3")(request2)
    status(result2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result2)}")

    val request3 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result3 = alertCtrl.get("testType;testSource;ref3")(request3)
    status(result3)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
    contentAsJson(result3).as[OutputAlert].status must beEqualTo("Ignored")

    val request4 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/markAsUnread")
      .withHeaders("user" -> "certuser@thehive.local")
    val result4 = alertCtrl.markAsUnread("testType;testSource;ref3")(request4)
    status(result4) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(result4)}")

    val request5 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result5 = alertCtrl.get("testType;testSource;ref3")(request5)
    status(result5)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result5)}")
    contentAsJson(result5).as[OutputAlert].status must beEqualTo("New")
  }

  "follow/unfollow an alert" in testApp { app =>
    import app.thehiveModuleV0._

    val request1 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result1 = alertCtrl.get("testType;testSource;ref3")(request1)
    status(result1)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")
    contentAsJson(result1).as[OutputAlert].follow must beTrue

    val request2 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/unfollow")
      .withHeaders("user" -> "certuser@thehive.local")
    val result2 = alertCtrl.unfollowAlert("testType;testSource;ref3")(request2)
    status(result2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result2)}")

    val request3 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result3 = alertCtrl.get("testType;testSource;ref3")(request3)
    status(result3)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
    contentAsJson(result3).as[OutputAlert].follow must beFalse

    val request4 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref3/follow")
      .withHeaders("user" -> "certuser@thehive.local")
    val result4 = alertCtrl.followAlert("testType;testSource;ref3")(request4)
    status(result4) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(result4)}")

    val request5 = FakeRequest("GET", "/api/v0/alert/testType;testSource;ref3")
      .withHeaders("user" -> "certuser@thehive.local")
    val result5 = alertCtrl.get("testType;testSource;ref3")(request5)
    status(result5)                               must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result5)}")
    contentAsJson(result5).as[OutputAlert].follow must beTrue
  }

  "create a case from an alert" in testApp { app =>
    import app._
    import app.thehiveModule._
    import app.thehiveModuleV0._

    val request1 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref5/createCase")
      .withHeaders("user" -> "certuser@thehive.local")
    val result1 = alertCtrl.createCase("testType;testSource;ref5")(request1)
    status(result1) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

    val resultCase       = contentAsJson(result1)
    val resultCaseOutput = resultCase.as[OutputCase]

    val expected = TestCase(
      caseId = resultCaseOutput.caseId,
      title = "[SPAM] alert#5",
      description = "description of alert #5",
      severity = 2,
      startDate = resultCaseOutput.startDate,
      endDate = None,
      flag = false,
      tlp = 2,
      pap = 2,
      status = "Open",
      tags = Set(
        "alert",
        "test",
        "spam",
        "src:mail"
      ),
      summary = None,
      owner = Some("certuser@thehive.local"),
      customFields = Json.obj(
        "boolean1" -> Json.obj("boolean" -> JsNull, "order" -> 1),
        "string1"  -> Json.obj("string" -> "string1 custom field", "order" -> 0)
      ),
      stats = Json.obj()
    )

    TestCase(resultCaseOutput) must_=== expected
    val observables = database.roTransaction { implicit graph =>
      val authContext = DummyUserSrv(organisation = "cert").authContext
      caseSrv.get(EntityIdOrName(resultCaseOutput._id)).observables(authContext).richObservable.toList
    }
    observables must contain(
      exactly(
        beLike[RichObservable] {
          case obs if obs.dataType == "domain" && obs.data.contains("c.fr") => ok
        }
      )
    )
  }

  "merge an alert with a case" in testApp { app =>
    import app._
    import app.thehiveModule._
    import app.thehiveModuleV0._

    val request1 = FakeRequest("POST", "/api/v0/alert/testType;testSource;ref5/merge/#1")
      .withHeaders("user" -> "certuser@thehive.local")
    val result1 = alertCtrl.mergeWithCase("testType;testSource;ref5", "1")(request1)
    status(result1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")

    val resultCase       = contentAsJson(result1)
    val resultCaseOutput = resultCase.as[OutputCase]

    resultCaseOutput.description.contains("Merged with alert #ref5") must beTrue

    database.roTransaction { implicit graph =>
      val observables = caseSrv
        .get(EntityIdOrName("1"))
        .observables(DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").getSystemAuthContext)
        .toList

      observables.flatMap(_.message) must contain("This domain")
    }
  }

  "delete an alert" in testApp { app =>
//    database.roTransaction { implicit graph =>
//      observableSrv
//        .initSteps
//        .has("message", "if you are lost")
//        .alert
//        .getOrFail() must beSuccessfulTry
//
//      val request1 = FakeRequest("DELETE", "/api/v0/alert/testType;testSource;ref4")
//        .withHeaders("user" -> "certuser@thehive.local")
//      val result1 = alertCtrl.delete("testType;testSource;ref4")(request1)
//
//      status(result1) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result1)}")
//      database.roTransaction(graph =>
//        observableSrv
//          .initSteps(graph)
//          .has("message", "if you are lost")
//          .alert
//          .getOrFail() must beFailedTry
//      )
//    }
    pending
  }
}
