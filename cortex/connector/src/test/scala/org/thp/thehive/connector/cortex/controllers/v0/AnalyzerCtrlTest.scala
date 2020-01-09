package org.thp.thehive.connector.cortex.controllers.v0

import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.dto.v0.OutputWorker

class AnalyzerCtrlTest extends PlaySpecification with TestAppBuilder {
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
