package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.controllers.FakeTemporaryFile
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.connector.cortex.dto.v0.OutputAnalyzerTemplate
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.{FakeRequest, PlaySpecification}

import scala.util.Random

class AnalyzerTemplateCtrlTest extends PlaySpecification with TestAppBuilder {

  "report controller" should {
//      "create, fetch, update and delete a template" in testApp {app =>
//
//      }

    "import valid templates contained in a zip file and fetch them by id and type" in testApp { app =>
      import app.cortexConnector.analyzerTemplateCtrl

      val file = FilePart("templates", "report-templates.zip", Option("application/zip"), FakeTemporaryFile.fromResource("/report-templates.zip"))
      val request = FakeRequest("POST", s"/api/connector/cortex/report/template/_import")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(MultipartFormData(Map.empty, Seq(file), Nil)))

      val result = analyzerTemplateCtrl.importTemplates(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val importedList = contentAsJson(result)

      importedList must equalTo(
        Json.obj(
          "JoeSandbox_File_Analysis_Noinet_2_0" -> true,
          "Yeti_1_0"                            -> true,
          "testAnalyzer_short"                  -> true
        )
      )

      val getRequest = FakeRequest("GET", s"/api/connector/cortex/report/template/content/JoeSandbox_File_Analysis_Noinet_2_0/long")
        .withHeaders("user" -> "admin@thehive.local")
      val getResult = analyzerTemplateCtrl.get("JoeSandbox_File_Analysis_Noinet_2_0")(getRequest)
      status(getResult) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult)}")

      val createRequest = FakeRequest("POST", "/api/connector/cortex/report/template")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse(s"""
              {
                "analyzerId": "anaTest1",
                "content": "<span>${Random.alphanumeric.take(10).mkString}</span>"
              }
            """.stripMargin))
      val createResult = analyzerTemplateCtrl.create(createRequest)
      status(createResult) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(createResult)}")

      val outputAnalyzerTemplate = contentAsJson(createResult).as[OutputAnalyzerTemplate]
      val getRequest2 = FakeRequest("GET", s"/api/connector/cortex/analyzer/template/${outputAnalyzerTemplate.id}")
        .withHeaders("user" -> "admin@thehive.local")
      val getResult2 = analyzerTemplateCtrl.get(outputAnalyzerTemplate.id)(getRequest2)
      status(getResult2) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult2)}")

      val updateRequest = FakeRequest("PATCH", s"/api/connector/cortex/analyzer/template/${outputAnalyzerTemplate.id}")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"content": "<br/>"}"""))
      val updateResult = analyzerTemplateCtrl.update(outputAnalyzerTemplate.id)(updateRequest)
      status(updateResult)                                           must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
      contentAsJson(updateResult).as[OutputAnalyzerTemplate].content must equalTo("<br/>")

      val deleteRequest = FakeRequest("DELETE", s"/api/connector/cortex/report/template/${outputAnalyzerTemplate.id}")
        .withHeaders("user" -> "admin@thehive.local")
      val deleteResult = analyzerTemplateCtrl.delete(outputAnalyzerTemplate.id)(deleteRequest)
      status(deleteResult) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
    }
  }
}
