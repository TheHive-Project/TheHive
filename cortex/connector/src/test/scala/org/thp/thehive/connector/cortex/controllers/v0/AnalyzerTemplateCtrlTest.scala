package org.thp.thehive.connector.cortex.controllers.v0

import scala.util.{Random, Try}

import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.dto.v0.OutputAnalyzerTemplate
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.controllers.FakeTemporaryFile
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.services.CortexActor
import org.thp.thehive.models.{DatabaseBuilder, Permissions}

class AnalyzerTemplateCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val reportCtrl: AnalyzerTemplateCtrl = app.instanceOf[AnalyzerTemplateCtrl]
    val cortexQueryExecutor              = app.instanceOf[CortexQueryExecutor]

    s"[$name] report controller" should {
//      "create, fetch, update and delete a template" in {
//
//      }

      "import valid templates contained in a zip file and fetch them by id and type" in {
        val file = FilePart("templates", "report-templates.zip", Option("application/zip"), FakeTemporaryFile.fromResource("/report-templates.zip"))
        val request = FakeRequest("POST", s"/api/connector/cortex/report/template/_import")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
          .withBody(AnyContentAsMultipartFormData(MultipartFormData(Map.empty, Seq(file), Nil)))

        val result = reportCtrl.importTemplates(request)

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
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
        val getResult = reportCtrl.get("JoeSandbox_File_Analysis_Noinet_2_0")(getRequest)
        status(getResult) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult)}")

        val createRequest = FakeRequest("POST", "/api/connector/cortex/report/template")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                "analyzerId": "anaTest1",
                "content": "<span>${Random.alphanumeric.take(10).mkString}</span>"
              }
            """.stripMargin))
        val createResult = reportCtrl.create(createRequest)
        status(createResult) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(createResult)}")

        val outputAnalyzerTemplate = contentAsJson(createResult).as[OutputAnalyzerTemplate]
        val getRequest2 = FakeRequest("GET", s"/api/connector/cortex/analyzer/template/${outputAnalyzerTemplate.id}")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
        val getResult2 = reportCtrl.get(outputAnalyzerTemplate.id)(getRequest2)
        status(getResult2) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult2)}")

        val updateRequest = FakeRequest("PATCH", s"/api/connector/cortex/analyzer/template/${outputAnalyzerTemplate.id}")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
          .withJsonBody(Json.parse("""{"content": "<br/>"}"""))
        val updateResult = reportCtrl.update(outputAnalyzerTemplate.id)(updateRequest)
        status(updateResult) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
        contentAsJson(updateResult).as[OutputAnalyzerTemplate].content must equalTo("<br/>")

        val deleteRequest = FakeRequest("DELETE", s"/api/connector/cortex/report/template/${outputAnalyzerTemplate.id}")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
        val deleteResult = reportCtrl.delete(outputAnalyzerTemplate.id)(deleteRequest)
        status(deleteResult) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
      }

      "search templates properly" in {
        val requestSearch = FakeRequest("POST", s"/api/connector/cortex/report/template/_search?range=0-200")
          .withHeaders("user" -> "admin@thehive.local", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                 "query":{"analyzerId": "Yeti_1_0"}
              }
            """.stripMargin))
        val resultSearch = cortexQueryExecutor.report.search(requestSearch)

        status(resultSearch) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultSearch)}")
      }
    }
  }
}
