package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.FakeTemporaryFile
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.OutputPattern
import play.api.libs.json.{JsArray, JsString}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.{FakeRequest, PlaySpecification}

case class TestPattern(
    patternId: String,
    name: String,
    description: Option[String],
    tactics: Set[String],
    url: String,
    patternType: String,
    capecId: Option[String],
    capecUrl: Option[String],
    revoked: Boolean,
    dataSources: Seq[String],
    defenseBypassed: Seq[String],
    detection: Option[String],
    permissionsRequired: Seq[String],
    platforms: Seq[String],
    remoteSupport: Boolean,
    systemRequirements: Seq[String],
    version: Option[String]
)

object TestPattern {
  def apply(outputPattern: OutputPattern): TestPattern =
    outputPattern.into[TestPattern].transform
}

class PatternCtrlTest extends PlaySpecification with TestAppBuilder {
  "pattern controller" should {
    "import json patterns" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/pattern/import/attack")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(
          AnyContentAsMultipartFormData(
            MultipartFormData(
              dataParts = Map.empty,
              files = Seq(FilePart("file", "patterns.json", Option("application/json"), FakeTemporaryFile.fromResource("/patterns.json"))),
              badParts = Seq()
            )
          )
        )

      val result = app[PatternCtrl].importMitre(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result)
        .as[JsArray]
        .value
        .count(jsValue => (jsValue \ "status").get == JsString("Success")) must beEqualTo(8)
    }

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
        Set("testTactic1", "testTactic2"),
        "http://test.pattern.url",
        "unit-test",
        None,
        None,
        revoked = false,
        Seq(),
        Seq(),
        None,
        Seq(),
        Seq(),
        remoteSupport = true,
        Seq(),
        Some("1.0")
      )
    }

    "get patterns linked to case" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/pattern/case/1")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[PatternCtrl].getCasePatterns("1")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[JsArray].value.size must beEqualTo(2)
    }

    "import & update a pattern" in testApp { app =>
      // Get a pattern
      val request1 = FakeRequest("GET", "/api/v1/pattern/T123")
        .withHeaders("user" -> "certuser@thehive.local")

      val result1 = app[PatternCtrl].get("T123")(request1)
      status(result1) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")
      val result1Pattern = contentAsJson(result1).as[OutputPattern]

      TestPattern(result1Pattern) must_=== TestPattern(
        "T123",
        "testPattern1",
        Some("The testPattern 1"),
        Set("testTactic1", "testTactic2"),
        "http://test.pattern.url",
        "unit-test",
        None,
        None,
        revoked = false,
        Seq(),
        Seq(),
        None,
        Seq(),
        Seq(),
        remoteSupport = true,
        Seq(),
        Some("1.0")
      )

      // Update a pattern
      val request2 = FakeRequest("POST", "/api/v1/pattern/import/attack")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(
          AnyContentAsMultipartFormData(
            MultipartFormData(
              dataParts = Map.empty,
              files =
                Seq(FilePart("file", "patternsUpdate.json", Option("application/json"), FakeTemporaryFile.fromResource("/patternsUpdate.json"))),
              badParts = Seq()
            )
          )
        )

      val result2 = app[PatternCtrl].importMitre(request2)
      status(result2) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result2)}")

      // Check for updates
      val request3 = FakeRequest("GET", "/api/v1/pattern/T123")
        .withHeaders("user" -> "certuser@thehive.local")

      val result3 = app[PatternCtrl].get("T123")(request3)
      status(result3) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
      val result3Pattern = contentAsJson(result3).as[OutputPattern]

      TestPattern(result3Pattern) must_=== TestPattern(
        "T123",
        "Updated testPattern1",
        None,
        Set(),
        "https://attack.mitre.org/techniques/T123",
        "attack-pattern",
        None,
        None,
        revoked = true,
        Seq(),
        Seq(),
        None,
        Seq(),
        Seq(),
        remoteSupport = false,
        Seq(),
        None
      )
    }

    "delete a pattern" in testApp { app =>
      val request1 = FakeRequest("GET", "/api/v1/pattern/testPattern1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result1 = app[PatternCtrl].get("T123")(request1)
      status(result1) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("DELETE", "/api/v1/pattern/testPattern1")
        .withHeaders("user" -> "admin@thehive.local")
      val result2 = app[PatternCtrl].delete("T123")(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/pattern/testPattern1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = app[PatternCtrl].get("T123")(request3)
      status(result3) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }

  }
}
