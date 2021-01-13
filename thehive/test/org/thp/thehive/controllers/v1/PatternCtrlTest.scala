package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.OutputPattern
import play.api.test.{FakeRequest, PlaySpecification}

case class TestPattern(
    patternId: String,
    name: String,
    description: Option[String],
    tactics: Seq[String],
    url: String,
    patternType: String,
    platforms: Seq[String],
    dataSources: Seq[String],
    version: Option[String]
)

object TestPattern {
  def apply(outputPattern: OutputPattern): TestPattern =
    outputPattern.into[TestPattern].transform
}

class PatternCtrlTest extends PlaySpecification with TestAppBuilder {
  "pattern controller" should {
    // TODO
    /*
    "import json patterns" in testApp { app =>

    }
     */

    "get a existing pattern" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/pattern/T123")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[PatternCtrl].get("T123")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultPattern = contentAsJson(result).as[OutputPattern]

      TestPattern(resultPattern) must_=== TestPattern(
        "T123",
        "testPattern1",
        Some("The testPattern 1"),
        Seq("testTactic1", "testTactic2"),
        "http://test.pattern.url",
        "unit-test",
        Seq(),
        Seq(),
        Some("1.0")
      )
    }
  }
}
