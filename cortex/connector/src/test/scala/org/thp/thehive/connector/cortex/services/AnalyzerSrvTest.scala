package org.thp.thehive.connector.cortex.services

import org.thp.cortex.dto.v0.OutputWorker
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Permissions
import play.api.test.PlaySpecification

class AnalyzerSrvTest extends PlaySpecification with TestAppBuilder {
  override val databaseName: String = "thehiveCortex"
//  override def appConfigure: AppBuilder =
//    super
//      .appConfigure
////      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])
//      .`override`(
//        _.bindActor[CortexActor]("cortex-actor")
//          .bindToProvider[CortexClient, TestCortexClientProvider]
//          .bind[Connector, TestConnector]
////          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
//      )

  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext
  "analyzer service" should {
    "get a list of Cortex workers" in testApp { app =>
      val r = await(app[AnalyzerSrv].listAnalyzer(Some("all")))
      val outputWorker2 =
        OutputWorker(
          "anaTest2",
          "anaTest2",
          "2",
          "nos hoc tempore in provinciis decernendis perpetuae pacis",
          Seq("test", "dummy"),
          2,
          2
        )
      val outputWorker1 =
        OutputWorker(
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

    "get Cortex worker by id" in testApp { app =>
      val r = await(app[AnalyzerSrv].getAnalyzer("anaTest2"))
      val outputWorker =
        OutputWorker(
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

    "get a list of Cortex workers by dataType" in testApp { app =>
      val r = await(app[AnalyzerSrv].listAnalyzerByType("test"))
      val outputWorker2 =
        OutputWorker(
          "anaTest2",
          "anaTest2",
          "2",
          "nos hoc tempore in provinciis decernendis perpetuae pacis",
          Seq("test", "dummy"),
          2,
          2
        )
      val outputWorker1 =
        OutputWorker(
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
