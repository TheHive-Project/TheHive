package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.connector.cortex.dto.v0.OutputWorker
import play.api.test.{FakeRequest, PlaySpecification}

class AnalyzerCtrlTest extends PlaySpecification with TestAppBuilder {

  "analyzer controller" should {
    "list analyzers" in testApp { app =>
      import app.cortexConnector.analyzerCtrl

      val request = FakeRequest("GET", s"/api/connector/cortex/analyzer?range=all").withHeaders("user" -> "certuser@thehive.local")
      val result  = analyzerCtrl.list(request)

      status(result) shouldEqual 200

      val resultList = contentAsJson(result).as[Seq[OutputWorker]]

      resultList.length must beEqualTo(2)
    }
  }
}
