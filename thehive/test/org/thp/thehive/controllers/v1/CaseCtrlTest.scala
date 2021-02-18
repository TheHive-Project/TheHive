package org.thp.thehive.controllers.v1

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputCase, OutputCase, OutputCustomFieldValue}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

case class TestCase(
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    user: Option[String],
    customFields: Seq[TestCustomFieldValue] = Seq.empty
)

object TestCase {
  def apply(outputCase: OutputCase): TestCase =
    TestCase(
      outputCase.title,
      outputCase.description,
      outputCase.severity,
      outputCase.startDate,
      outputCase.endDate,
      outputCase.tags,
      outputCase.flag,
      outputCase.tlp,
      outputCase.pap,
      outputCase.status,
      outputCase.summary,
      outputCase.assignee,
      outputCase.customFields.map(TestCustomFieldValue.apply).sortBy(_.order)
    )
}

case class TestCustomFieldValue(name: String, description: String, `type`: String, value: JsValue, order: Int)

object TestCustomFieldValue {
  def apply(outputCustomFieldValue: OutputCustomFieldValue): TestCustomFieldValue =
    TestCustomFieldValue(
      outputCustomFieldValue.name,
      outputCustomFieldValue.description,
      outputCustomFieldValue.`type`,
      outputCustomFieldValue.value,
      outputCustomFieldValue.order
    )
}

class CaseCtrlTest extends PlaySpecification with TestAppBuilder {
  "case controller" should {
    "create a new case" in testApp { app =>
      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJson(
            InputCase(
              title = "case title (create case test)",
              description = "case description (create case test)",
              severity = Some(2),
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3)
            )
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[CaseCtrl].create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "case title (create case test)",
        description = "case description (create case test)",
        severity = 2,
        startDate = now,
        endDate = None,
        tags = Set("tag1", "tag2"),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        summary = None,
        user = Some("certuser@thehive.local"),
        customFields = Seq.empty
      )

      TestCase(resultCase) must_=== expected
    }

    "create a new case using a template" in testApp { app =>
      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJsObject(
            InputCase(
              title = "case title (create case test with template)",
              description = "case description (create case test with template)",
              severity = None,
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3)
            )
          ) + ("caseTemplate" -> JsString("spam"))
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[CaseCtrl].create(request)
      status(result) must_=== 201
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "[SPAM] case title (create case test with template)",
        description = "case description (create case test with template)",
        severity = 1,
        startDate = now,
        endDate = None,
        tags = Set("tag1", "tag2", "testNamespace:testPredicate=\"spam\"", "testNamespace:testPredicate=\"src:mail\""),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        summary = None,
        user = Some("certuser@thehive.local"),
        customFields = Seq(
          TestCustomFieldValue("string1", "string custom field", "string", JsString("string1 custom field"), 0),
          TestCustomFieldValue("boolean1", "boolean custom field", "boolean", JsNull, 1)
        )
      )

      TestCase(resultCase) must_=== expected
    }

    "get a case" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/case/1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result     = app[CaseCtrl].get("1")(request)
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "case#1",
        description = "description of case #1",
        severity = 2,
        startDate = new Date(1531667370000L),
        endDate = None,
        tags = Set("testNamespace:testPredicate=\"t1\"", "testNamespace:testPredicate=\"t3\""),
        flag = false,
        tlp = 2,
        pap = 2,
        status = "Open",
        user = Some("certuser@thehive.local")
      )

      TestCase(resultCase) must_=== expected
    }

    "update a case" in testApp { app =>
//        val updateRequest = FakeRequest("PATCH", s"/api/v1/case/#2")
//          .withJsonBody(
//            Json.obj(
//              "title"  → "new title",
//              "flag"   → false,
//              "tlp"    → 2,
//              "pap"    → 1,
//              "status" → "resolved"
//            ))
//          .withHeaders("user" → "certuser@thehive.local")
//        val updateResult = app[CaseCtrl].update("#2")(updateRequest)
//        status(updateResult) must_=== 204
//
//        val getRequest = FakeRequest("GET", s"/api/v1/case/#2")
//        val getResult  = app[CaseCtrl].get("#2")(getRequest)
//        val resultCase = contentAsJson(getResult).as[OutputCase]
//        val expected = TestCase(
//          title = "new title",
//          description = "case description (update case test)",
//          severity = 2,
//          startDate = new Date(),
//          endDate = None,
//          tags = Set("tag1", "tag2"),
//          flag = false,
//          tlp = 2,
//          pap = 1,
//          status = "resolved",
//          user = Some(dummyUserSrv.authContext.userId)
//        )

//        TestCase(resultCase) must_=== expected
      pending
    }

    "merge 3 cases correctly" in testApp { app =>
      val request21 = FakeRequest("GET", s"/api/v1/case/#21")
        .withHeaders("user" -> "certuser@thehive.local")
      val case21 = app[CaseCtrl].get("21")(request21)
      status(case21) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(case21)}")
      val output21 = contentAsJson(case21).as[OutputCase]

      val request = FakeRequest("GET", "/api/v1/case/_merge/21,22,23")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21,22,23")(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val outputCase = contentAsJson(result).as[OutputCase]
      // TODO Tags / CustomFields / ResolutionStatus / ImpactStatus / Alerts / Share / CaseTemplate ?

      // Merge result
      TestCase(outputCase) must equalTo(
        TestCase(
          title = "case#21 / case#22 / case#23",
          description = "description of case #21\n\ndescription of case #22\n\ndescription of case #23",
          severity = 3,
          startDate = output21.startDate,
          endDate = output21.endDate,
          Set.empty,
          flag = true,
          tlp = 4,
          pap = 3,
          status = "Open",
          None,
          Some("certuser@thehive.local"),
          Seq()
        )
      )

      // Merged cases should be deleted
      val deleted21 = app[CaseCtrl].get("21")(request)
      status(deleted21) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted21)}")
      val deleted22 = app[CaseCtrl].get("22")(request)
      status(deleted22) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted22)}")
      val deleted23 = app[CaseCtrl].get("23")(request)
      status(deleted23) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted23)}")
    }

    "merge two cases error, not same organisation" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/case/_merge/21,24")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21,24")(request)
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
      (contentAsJson(result) \ "message").as[String] must contain("different organisations")
    }

    "merge two cases error, not same profile" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/case/_merge/21,25")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21,25")(request)
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
      (contentAsJson(result) \ "message").as[String] must contain("different profiles")
    }
  }
}
