package org.thp.thehive.connector.cortex.controllers.v0

import java.io.File

import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.models.{ReportTemplate, ReportType}
import org.thp.thehive.connector.cortex.services.ReportTemplateSrv
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
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
      .addConfiguration(
        Configuration(
          "play.modules.disabled" -> List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule")
        )
      )

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val reportCtrl: ReportCtrl = app.instanceOf[ReportCtrl]
    val reportTemplateSrv      = app.instanceOf[ReportTemplateSrv]
    val db: Database           = app.instanceOf[Database]

    s"[$name] report controller" should {
      "fetch a template by analyzerId and reportType" in {
        val template =
          db.transaction(graph => reportTemplateSrv.create(ReportTemplate("anaTest2", "test", ReportType.long))(graph, dummyUserSrv.authContext))
        val request = FakeRequest("GET", s"/api/connector/cortex/report/template/content/anaTest2/long")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
        val result = reportCtrl.getContent("anaTest2", "long")(request)

        status(result) shouldEqual 200
        contentAsString(result) shouldEqual template.content
      }

      "import valid templates contained in a zip file" in {
        val archive  = SingletonTemporaryFileCreator.create(new File(getClass.getResource("/report-templates.zip").toURI).toPath)
        val file     = FilePart("templates", "report-templates.zip", Option("application/zip"), archive)
        val request = FakeRequest("POST", s"/api/connector/cortex/report/template/_import")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withBody(AnyContentAsMultipartFormData(MultipartFormData(Map("" -> Seq("dummydata")), Seq(file), Nil)))

        val result = reportCtrl.importTemplates(request)

        val t = contentAsJson(result)

        status(result) shouldEqual 200
      }
    }
  }
}
