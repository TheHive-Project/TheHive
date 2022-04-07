package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseSrv
import play.api.libs.json._
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

case class TestCase(
    caseId: Int,
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
    owner: Option[String],
    customFields: JsObject = JsObject.empty,
    stats: JsValue
)

object TestCase {
  def apply(outputCase: OutputCase): TestCase =
    outputCase.into[TestCase].transform
}

class CaseCtrlTest extends PlaySpecification with TestAppBuilder {

  "case controller" should {

    "create a new case from spam template" in testApp { app =>
      val now = new Date()

      val inputCustomFields = Seq(
        InputCustomFieldValue("date1", Some(now.getTime), None),
        InputCustomFieldValue("boolean1", Some(true), None)
//          InputCustomFieldValue("string1", Some("string custom field"))
      )

      val request = FakeRequest("POST", "/api/v0/case")
        .withJsonBody(
          Json
            .toJson(
              InputCase(
                title = "case title (create case test)",
                description = "case description (create case test)",
                severity = Some(1),
                startDate = Some(now),
                tags = Set("tag1", "tag2"),
                flag = Some(false),
                tlp = Some(1),
                pap = Some(3),
                customFields = inputCustomFields
              )
            )
            .as[JsObject] + ("template" -> JsString("spam"))
        )
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].create(request)
      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCase       = contentAsJson(result)
      val resultCaseOutput = resultCase.as[OutputCase]
      val expected = TestCase(
        caseId = resultCaseOutput.caseId,
        title = "[SPAM] case title (create case test)",
        description = "case description (create case test)",
        severity = 1,
        startDate = now,
        endDate = None,
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        tags = Set("spam", "src:mail", "tag1", "tag2"),
        summary = None,
        owner = Some("certuser@thehive.local"),
        customFields = Json.obj(
          "boolean1" -> Json.obj("boolean" -> true, "order" -> 1),
          "string1"  -> Json.obj("string" -> "string1 custom field", "order" -> 2),
          "date1"    -> Json.obj("date" -> now.getTime, "order" -> 0)
        ),
        stats = Json.obj()
      )

      TestCase(resultCaseOutput) shouldEqual expected
    }

    "create a new case from scratch" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/case")
        .withJsonBody(
          Json
            .parse(
              """{
                     "status":"Open",
                     "severity":1,
                     "tlp":2,
                     "pap":2,
                     "title":"test 6",
                     "description":"desc ok",
                     "tags":[],
                     "tasks":[
                        {
                           "title":"task x",
                           "flag":false,
                           "status":"Waiting"
                        }
                     ],
                     "user":"certro@thehive.local"
                  }"""
            )
            .as[JsObject]
        )
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].create(request)
      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val outputCase = contentAsJson(result).as[OutputCase]
      TestCase(outputCase) must equalTo(
        TestCase(
          caseId = outputCase.caseId,
          title = "test 6",
          description = "desc ok",
          severity = 1,
          startDate = outputCase.startDate,
          flag = false,
          tlp = 2,
          pap = 2,
          status = "Open",
          tags = Set.empty,
          owner = Some("certro@thehive.local"),
          stats = JsObject.empty
        )
      )

      val requestList = FakeRequest("GET", "/api/case/task").withHeaders("user" -> "certuser@thehive.local")
      val resultList  = app[TaskCtrl].search(requestList)

      status(resultList) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultList)}")
      val tasksList = contentAsJson(resultList)(defaultAwaitTimeout, app[Materializer]).as[Seq[OutputTask]]
      tasksList.find(_.title == "task x") must beSome

      val assignee = app[Database].roTransaction(implicit graph => app[CaseSrv].get(EntityIdOrName(outputCase._id)).assignee.getOrFail("Case"))

      assignee must beSuccessfulTry
      assignee.get.login shouldEqual "certro@thehive.local"
    }

    "try to get a case" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/case/#2")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[CaseCtrl].get("145")(request)

      status(result) shouldEqual 404

      val result2 = app[CaseCtrl].get("2")(request)
      status(result2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result2)}")
      val resultCase       = contentAsJson(result2)
      val resultCaseOutput = resultCase.as[OutputCase]

      val expected = TestCase(
        caseId = 2,
        title = "case#2",
        description = "description of case #2",
        severity = 2,
        startDate = new Date(1531667370000L),
        endDate = None,
        flag = false,
        tlp = 2,
        pap = 2,
        status = "Open",
        tags = Set("t2", "t1"),
        summary = None,
        owner = Some("certuser@thehive.local"),
        customFields = JsObject.empty,
        stats = Json.obj()
      )

      TestCase(resultCaseOutput) must_=== expected
    }

    "update a case properly" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/v0/case/1")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(
          Json.obj(
            "title" -> "new title",
            "flag"  -> true
          )
        )
      val result = app[CaseCtrl].update("1")(request)
      status(result) must_=== 200
      val resultCase = contentAsJson(result).as[OutputCase]

      resultCase.title must equalTo("new title")
      resultCase.flag  must equalTo(true)
    }

    "update a bulk of cases properly" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/v0/case/_bulk")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(
          Json.obj(
            "ids"         -> List("1", "2"),
            "description" -> "new description",
            "tlp"         -> 1,
            "pap"         -> 1
          )
        )
      val result = app[CaseCtrl].bulkUpdate(request)
      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCases = contentAsJson(result).as[List[OutputCase]]
      resultCases must have size 2

      resultCases.map(_.description) must contain(be_==("new description")).forall
      resultCases.map(_.tlp)         must contain(be_==(1)).forall
      resultCases.map(_.pap)         must contain(be_==(1)).forall

      val requestGet1 = FakeRequest("GET", s"/api/v0/case/1")
        .withHeaders("user" -> "certuser@thehive.local")
      val resultGet1 = app[CaseCtrl].get("1")(requestGet1)
      status(resultGet1) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet1)}")
      val case1 = contentAsJson(resultGet1).as[OutputCase]

      val requestGet2 = FakeRequest("GET", s"/api/v0/case/2")
        .withHeaders("user" -> "certuser@thehive.local")
      val resultGet2 = app[CaseCtrl].get("2")(requestGet2)
      status(resultGet2) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultGet2)}")
      val case3 = contentAsJson(resultGet2).as[OutputCase]

      resultCases.map(TestCase.apply) must contain(exactly(TestCase(case1), TestCase(case3)))
    }

    "search cases" in testApp { app =>
      val request = FakeRequest("POST", s"/api/v0/case/_search?range=0-15&sort=-flag&sort=-startDate&nstats=true")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(
          Json.parse("""{"query":{"severity":2}}""")
        )
      val result = app[CaseCtrl].search()(request)
      status(result)            must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      header("X-Total", result) must beSome("2")
      val resultCases = contentAsJson(result)(defaultAwaitTimeout, app[Materializer]).as[Seq[OutputCase]]

      resultCases.map(_.caseId) must contain(exactly(1, 2))
    }

    "search a case by custom field" in testApp { app =>
//      // Create a case with custom fields
//      val now = new Date()
//      val inputCustomFields = Seq(
//        InputCustomFieldValue("date1", Some(now.getTime), None),
//        InputCustomFieldValue("boolean1", Some(true), None)
//      )
//
//      val request = FakeRequest("POST", "/api/v0/case")
//        .withJsonBody(
//          Json
//            .toJson(
//              InputCase(
//                title = "cf case",
//                description = "cf case description",
//                severity = Some(2),
//                startDate = Some(now),
//                tags = Set("tag1cf", "tag2cf"),
//                flag = Some(false),
//                tlp = Some(2),
//                pap = Some(2),
//                customFields = inputCustomFields
//              )
//            )
//            .as[JsObject] + ("template" -> JsString("spam"))
//        )
//        .withHeaders("user" -> "certuser@thehive.local")
//
//      val result = app[CaseCtrl].create(request)
//      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      // Search it by cf value
      val requestSearch = FakeRequest("POST", s"/api/v0/case/_search?range=0-15&sort=-flag&sort=-startDate&nstats=true")
        .withHeaders("user" -> "socuser@thehive.local")
        .withJsonBody(
          Json.parse("""{"query":{"_and":[{"_field":"customFields.boolean1","_value":true}]}}""")
        )
      val resultSearch = app[CaseCtrl].search()(requestSearch)
      status(resultSearch)                                                                     must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultSearch)}")
      contentAsJson(resultSearch)(defaultAwaitTimeout, app[Materializer]).as[List[OutputCase]] must not(beEmpty)
    }

    "get and aggregate properly case stats" in testApp { app =>
      val request = FakeRequest("POST", s"/api/v0/case/_stats")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(
          Json.parse("""{
                            "query": {},
                            "stats":[
                               {
                                  "_agg":"field",
                                  "_field":"tags",
                                  "_select":[
                                     {
                                        "_agg":"count"
                                     }
                                  ],
                                  "_size":1000
                               },
                               {
                                  "_agg":"count"
                               }
                            ]
                         }""")
        )
      val result = app[CaseCtrl].stats()(request)
      status(result) must_=== 200
      val resultCase = contentAsJson(result)

      (resultCase \ "count").asOpt[Int]        must beSome(10)
      (resultCase \ "t1" \ "count").asOpt[Int] must beSome(2)
      (resultCase \ "t2" \ "count").asOpt[Int] must beSome(1)
      (resultCase \ "t3" \ "count").asOpt[Int] must beSome(1)
    }

    "assign a case to an user" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/v0/case/4")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.obj("owner" -> "certro@thehive.local"))
      val result = app[CaseCtrl].update("1")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCase       = contentAsJson(result)
      val resultCaseOutput = resultCase.as[OutputCase]

      resultCaseOutput.owner should beSome("certro@thehive.local")
    }

    "force delete a case" in testApp { app =>
      val tasks = app[Database].roTransaction { implicit graph =>
        val authContext = DummyUserSrv(organisation = "cert").authContext
        app[CaseSrv].get(EntityIdOrName("1")).tasks(authContext).toSeq
      }
      tasks must have size 2

      val requestDel = FakeRequest("DELETE", s"/api/v0/case/#1/force")
        .withHeaders("user" -> "certuser@thehive.local")
      val resultDel = app[CaseCtrl].delete("1")(requestDel)
      status(resultDel) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(resultDel)}")

      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].get(EntityIdOrName("1")).headOption must beNone
//        tasks.flatMap(task => app[TaskSrv].get(task).headOption) must beEmpty
      }
    }

    "merge two cases correctly" in testApp { app =>
      val request21 = FakeRequest("GET", s"/api/v0/case/#21")
        .withHeaders("user" -> "certuser@thehive.local")
      val case21 = app[CaseCtrl].get("21")(request21)
      status(case21) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(case21)}")
      val output21 = contentAsJson(case21).as[OutputCase]

      val request = FakeRequest("POST", "/api/v0/case/21/_merge/22")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21", "22")(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val outputCase = contentAsJson(result).as[OutputCase]

      // Merge result
      TestCase(outputCase) must equalTo(
        TestCase(
          caseId = 36,
          title = "case#21 / case#22",
          description = "description of case #21\n\ndescription of case #22",
          severity = 3,
          startDate = output21.startDate,
          flag = true,
          tlp = 4,
          pap = 3,
          status = "Open",
          tags = Set("toMerge:pred1=\"value1\"", "toMerge:pred2=\"value2\""),
          owner = Some("certuser@thehive.local"),
          stats = JsObject.empty
        )
      )

      // Merged cases should be deleted
      val deleted21 = app[CaseCtrl].get("21")(request)
      status(deleted21) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted21)}")
      val deleted22 = app[CaseCtrl].get("22")(request)
      status(deleted22) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted22)}")
    }

    "merge two cases error, not same organisation" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/case/21/_merge/24")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21", "24")(request)
      // User shouldn't be able to see others cases, resulting in 404
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "merge two cases error, not same profile" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/case/21/_merge/25")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[CaseCtrl].merge("21", "25")(request)
      status(result)                              must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
    }
  }
}
