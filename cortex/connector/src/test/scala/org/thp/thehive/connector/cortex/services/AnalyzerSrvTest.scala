package org.thp.thehive.connector.cortex.services

import scala.util.Try

import play.api.test.{NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.cortex.dto.v0.OutputCortexWorker
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}

class AnalyzerSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] analyzer service" should {
      val analyzerSrv = app.instanceOf[AnalyzerSrv]

      "get a list of Cortex workers" in {
        val r = await(analyzerSrv.listAnalyzer(dummyUserSrv.authContext))
        val outputWorker2 =
          OutputCortexWorker(
            "anaTest2",
            "anaTest2",
            "2",
            "nos hoc tempore in provinciis decernendis perpetuae pacis",
            Seq("test", "dummy"),
            2,
            2
          )
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
        val r = await(analyzerSrv.getAnalyzer("anaTest2")(dummyUserSrv.authContext))
        val outputWorker =
          OutputCortexWorker(
            "anaTest2",
            "anaTest2",
            "2",
            "nos hoc tempore in provinciis decernendis perpetuae pacis",
            Seq("test", "dummy"),
            2,
            2
          )

        r shouldEqual ((outputWorker, Seq("test")))
      }

      "get a list of Cortex workers by dataType" in {
        val r = await(analyzerSrv.listAnalyzerByType("test")(dummyUserSrv.authContext))
        val outputWorker2 =
          OutputCortexWorker(
            "anaTest2",
            "anaTest2",
            "2",
            "nos hoc tempore in provinciis decernendis perpetuae pacis",
            Seq("test", "dummy"),
            2,
            2
          )
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
    }
}
