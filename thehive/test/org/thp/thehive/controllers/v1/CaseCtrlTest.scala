package org.thp.thehive.controllers.v1

import java.util.Date

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputCase, OutputCase, OutputCustomFieldValue}
import play.api.libs.json.{JsNull, JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}

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
    customFields: Set[OutputCustomFieldValue] = Set.empty
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
      outputCase.customFields
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
        customFields = Set.empty
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
        tags = Set("tag1", "tag2", "testNamespace.testPredicate=\"spam\"", "testNamespace.testPredicate=\"src:mail\""),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        summary = None,
        user = Some("certuser@thehive.local"),
        customFields = Set(
          OutputCustomFieldValue("boolean1", "boolean custom field", "boolean", JsNull, 0),
          OutputCustomFieldValue("string1", "string custom field", "string", JsString("string1 custom field"), 0)
        )
      )

      TestCase(resultCase) must_=== expected
    }

    "get a case" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/case/#1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result     = app[CaseCtrl].get("#1")(request)
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "case#1",
        description = "description of case #1",
        severity = 2,
        startDate = new Date(1531667370000L),
        endDate = None,
        tags = Set("testNamespace.testPredicate=\"t1\"", "testNamespace.testPredicate=\"t3\""),
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
  }
}
