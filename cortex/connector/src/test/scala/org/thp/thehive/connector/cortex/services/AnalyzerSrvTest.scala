package org.thp.thehive.connector.cortex.services

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{DatabaseBuilder, _}
import org.thp.thehive.services._
import play.api.test.{NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.Try

class AnalyzerSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] analyzer service" should {
      val analyzerSrv = app.instanceOf[AnalyzerSrv]

      "get a list of Cortex workers" in {
        val r = await(analyzerSrv.listAnalyzer)
        val outputWorker2 =
          OutputCortexWorker("anaTest2", "anaTest2", "2", "nos hoc tempore in provinciis decernendis perpetuae pacis", Seq("test", "dummy"), 2, 2)
        val outputWorker1 =
          OutputCortexWorker(
            "anaTest1",
            "anaTest1",
            "1",
            "Ego vero sic intellego, Patres conscripti, nos hoc tempore in provinciis decernendis perpetuae pacis",
            Seq("test"),
            3,
            3
          )

        r shouldEqual Map(outputWorker2 -> Seq("test"), outputWorker1 -> Seq("test"))
      }

      "get Cortex worker by id" in {
        val r = await(analyzerSrv.getAnalyzer("anaTest2"))
        val outputWorker =
          OutputCortexWorker("anaTest2", "anaTest2", "2", "nos hoc tempore in provinciis decernendis perpetuae pacis", Seq("test", "dummy"), 2, 2)

        r shouldEqual ((outputWorker, Seq("test")))
      }
    }
}
