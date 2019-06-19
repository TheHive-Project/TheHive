package org.thp.thehive.connector.cortex.controllers.v0

import play.api.{ Configuration, Environment }

import akka.stream.Materializer
import org.thp.cortex.dto.v0.OutputAnalyzer
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.AuthenticateSrv
import org.thp.scalligraph.models.{ Database, Schema }
import org.thp.scalligraph.services.{ LocalFileSystemStorageSrv, StorageSrv }
import org.thp.thehive.models.{ Permissions, TheHiveSchema }
import org.thp.thehive.services.LocalUserSrv

class AnalyzerCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
//      .addConfiguration("play.modules.enabled += org.thp.thehive.connector.cortex.CortexModule")
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

        resultList.length shouldEqual 1
      }
    }
  }
}
