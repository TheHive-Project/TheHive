package org.thp.thehive.connector.cortex.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.FakeCortexClient
import org.thp.cortex.dto.v0.OutputAnalyzer
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class AnalyzerCtrlTest extends PlaySpecification with Mockito with FakeCortexClient {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration(
        Configuration(
          "play.modules.disabled" → List("org.thp.scalligraph.ScalligraphModule", "org.thp.thehive.TheHiveModule")
        )
      )
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val analyzerCtrl: AnalyzerCtrl      = app.instanceOf[AnalyzerCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] analyzer controller" should {
      "list analyzers" in {
        val request = FakeRequest("GET", s"/api/connector/cortex/analyzer?range=all").withHeaders("user" → "user1")
        val result  = analyzerCtrl.list(request)

        status(result) shouldEqual 200

        val resultList = contentAsJson(result).as[Seq[OutputAnalyzer]]

        resultList must beEmpty
      }

      "test" in withCortexClient { client ⇒
        await(client.listAnalyser).length shouldEqual 2
      }
    }
  }
}
