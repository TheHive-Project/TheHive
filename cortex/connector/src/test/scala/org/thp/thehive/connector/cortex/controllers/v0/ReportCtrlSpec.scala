package org.thp.thehive.connector.cortex.controllers.v0

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.dto.v0.OutputReportTemplate
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.services.CortexActor
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class ReportCtrlSpec extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[CortexActor]("cortex-actor")
      .addConfiguration(
        Configuration(
          "play.modules.disabled" -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule"),
          "akka.actor.provider"   -> "cluster"
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val reportCtrl: ReportCtrl = app.instanceOf[ReportCtrl]
    val cortexQueryExecutor    = app.instanceOf[CortexQueryExecutor]

    s"[$name] report controller" should {
      "create, fetch and delete a template" in {
        val request = FakeRequest("POST", s"/api/connector/cortex/report/template")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                "analyzerId": "dummy",
                "content": "<span>lol</span>",
                "reportType": "long"
              }
            """.stripMargin))
        val result = reportCtrl.create(request)

        status(result) shouldEqual 201

        val outputReportTemplate = contentAsJson(result).as[OutputReportTemplate]
        val requestGet = FakeRequest("GET", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")

        status(reportCtrl.get(outputReportTemplate.id)(requestGet)) shouldEqual 200

        val requestUpdate = FakeRequest("PATCH", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse("""{"content": "<br/>"}"""))
        val resultUpdate = reportCtrl.update(outputReportTemplate.id)(requestUpdate)

        status(resultUpdate) shouldEqual 200
        contentAsJson(resultUpdate).as[OutputReportTemplate].content shouldEqual "<br/>"

        val requestDel = FakeRequest("DELETE", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")

        status(reportCtrl.delete(outputReportTemplate.id)(requestDel)) shouldEqual 200
      }

      "import valid templates contained in a zip file and fetch them by id and type" in {
//        val archive = SingletonTemporaryFileCreator.create(new File(getClass.getResource("/report-templates.zip").toURI).toPath)
//        val file    = FilePart("templates", "report-templates.zip", Option("application/zip"), archive)
//        val request = FakeRequest("POST", s"/api/connector/cortex/report/template/_import")
//          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
//          .withBody(AnyContentAsMultipartFormData(MultipartFormData(Map("" -> Seq("dummydata")), Seq(file), Nil)))
//
//        val result = reportCtrl.importTemplates(request)
//
//        status(result) shouldEqual 200
//
//        val importedList = contentAsJson(result)
//
//        importedList shouldEqual Json.parse("""{
//                                                 "JoeSandbox_File_Analysis_Noinet_2_0_long":true,
//                                                 "JoeSandbox_File_Analysis_Noinet_2_0_short":true,
//                                                 "Yeti_1_0_long":true,
//                                                 "testAnalyzer_short.html_long":true
//                                              }""")
//
//        val requestGet = FakeRequest("GET", s"/api/connector/cortex/report/template/content/JoeSandbox_File_Analysis_Noinet_2_0/long")
//          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
//        val resultGet = reportCtrl.getContent("JoeSandbox_File_Analysis_Noinet_2_0", "long")(requestGet)
//
//        status(resultGet) shouldEqual 200
        pending("Raising a PermanentLockingException: Local lock contention for some reasons though no conflicting value is interfering")
      }

      "search templates properly" in {
        val requestSearch = FakeRequest("POST", s"/api/connector/cortex/report/template/_search?range=0-200")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                 "query":{"analyzerId": "Yeti_1_0"}
              }
            """.stripMargin))
        val resultSearch = cortexQueryExecutor.report.search(requestSearch)

        status(resultSearch) shouldEqual 200
      }
    }
  }
}
