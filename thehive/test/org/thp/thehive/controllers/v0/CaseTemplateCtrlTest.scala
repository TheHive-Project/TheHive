package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputCaseTemplate
import org.thp.thehive.services.CaseTemplateSrv

class CaseTemplateCtrlTest extends PlaySpecification with TestAppBuilder {
//  val dummyUserSrv = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)

//    def getAndTestCaseTemplate(name: String, description: String)(body: OutputCaseTemplate => MatchResult[Any]) = {
//      val json = s"""{
//            "name":"$name",
//            "titlePrefix":"test",
//            "severity":1,
//            "tlp":2,
//            "pap":2,
//            "tags":[
//               "tg${Random.nextInt}",
//               "tg${Random.nextInt}"
//            ],
//            "tasks":[
//               {
//                  "order":0,
//                  "title":"task template ${Random.nextInt}",
//                  "group":"default",
//                  "description":"Alios autem dicere aiunt multo etiam inhumanius (quem locum breviter paulo ante perstrinxi) praesidii adiumentique causa, non benevolentiae neque caritatis..."
//                }
//            ],
//            "customFields":{},
//            "description":"$description"
//          }""".stripMargin
//      val request = FakeRequest("POST", "/api/case/template")
//        .withHeaders("user" -> "certadmin@thehive.local")
//        .withJsonBody(
//          Json.parse(json)
//        )
//      val result = caseTemplateCtrl.create(request)
//
//      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      body(contentAsJson(result).as[OutputCaseTemplate])
//    }

  s"case template controller" should {
    "create a template" in testApp { app =>
      val json = Json.parse(
        """{
                    "name":"test case template",
                    "titlePrefix":"test",
                    "severity":1,
                    "tlp":2,
                    "pap":2,
                    "tags":[
                       "tg1",
                       "tg2"
                    ],
                    "tasks":[
                       {
                          "order":0,
                          "title":"task template 1",
                          "group":"default",
                          "description":"Alios autem dicere aiunt multo etiam inhumanius (quem locum breviter paulo ante perstrinxi) praesidii adiumentique causa, non benevolentiae neque caritatis..."
                        }
                    ],
                    "customFields":{},
                    "description":"test case template"
                  }"""
      )
      val request = FakeRequest("POST", "/api/case/template")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(json)
      val result = app[CaseTemplateCtrl].create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val output = contentAsJson(result).as[OutputCaseTemplate]
      output.tags.size shouldEqual 2
      output.name shouldEqual "test case template"
      output.tlp must beSome(2)
      output.pap must beSome(2)
      output.severity must beSome(1)
      output.tasks must not(beEmpty)
      output.tasks.head.title must beEqualTo("task template 1")
    }

    "get a template" in testApp { app =>
      val request = FakeRequest("GET", "/api/case/template/spam")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[CaseTemplateCtrl].get("spam")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "delete a template" in testApp { app =>
      val request = FakeRequest("DELETE", "/api/case/template/spam")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = app[CaseTemplateCtrl].delete("spam")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").headOption must beNone
      }
    }

    "update a template" in testApp { app =>
      val request = FakeRequest("PATCH", "/api/case/template/spam")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(
          Json.parse(s"""{
            "displayName": "patched",
            "titlePrefix":"test patched",
            "severity":2,
            "tlp":3,
            "pap":3,
            "tags":[
               "tg1",
               "spam",
               "src:mail"
            ],
            "customFields":{},
            "tasks": [{"title": "analysis"}],
            "description":"patched"
          }""")
        )
      val result = app[CaseTemplateCtrl].update("spam")(request)

      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      val updatedOutput = app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get("spam").richCaseTemplate.head()
      }

      updatedOutput.displayName shouldEqual "patched"
      updatedOutput.tags.size shouldEqual 3
      updatedOutput.name shouldEqual "spam"
      updatedOutput.tlp must beSome(3)
      updatedOutput.pap must beSome(3)
      updatedOutput.severity must beSome(2)
      updatedOutput.tasks must not(beEmpty)
      updatedOutput.tasks.head.title must beEqualTo("analysis")
    }
  }
}
