package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.dto.v0.OutputWorker
import org.thp.thehive.{BasicDatabaseProvider, TestAppBuilder}
import play.api.test.{FakeRequest, PlaySpecification}

class AnalyzerCtrlTest extends PlaySpecification with TestAppBuilder {
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .bindNamedToProvider[Database, BasicDatabaseProvider]("with-thehive-cortex-schema")

  "analyzer controller" should {
    "list analyzers" in testApp { app =>
      val request = FakeRequest("GET", s"/api/connector/cortex/analyzer?range=all").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[AnalyzerCtrl].list(request)

      status(result) shouldEqual 200

      val resultList = contentAsJson(result).as[Seq[OutputWorker]]

      resultList must beEmpty
    }
  }
}
