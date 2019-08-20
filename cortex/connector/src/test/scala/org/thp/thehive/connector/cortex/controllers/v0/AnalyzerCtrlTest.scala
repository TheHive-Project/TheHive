package org.thp.thehive.connector.cortex.controllers.v0

import scala.util.Try

import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.dto.v0.OutputWorker
import org.thp.thehive.connector.cortex.services.CortexActor
import org.thp.thehive.models.{DatabaseBuilder, Permissions}

class AnalyzerCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val analyzerCtrl: AnalyzerCtrl = app.instanceOf[AnalyzerCtrl]

    s"[$name] analyzer controller" should {
      "list analyzers" in {
        val request = FakeRequest("GET", s"/api/connector/cortex/analyzer?range=all").withHeaders("user" -> "user1")
        val result  = analyzerCtrl.list(request)

        status(result) shouldEqual 200

        val resultList = contentAsJson(result).as[Seq[OutputWorker]]

        resultList must beEmpty
      }
    }
  }
}
