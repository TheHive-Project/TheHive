package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl.TransformerOps
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputProcedure, OutputProcedure}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

case class TestProcedure(
    description: String,
    occurence: Date,
    patternId: String
)

object TestProcedure {
  def apply(outputProcedure: OutputProcedure): TestProcedure =
    outputProcedure.into[TestProcedure].transform
}

class ProcedureCtrlTest extends PlaySpecification with TestAppBuilder {
  "procedure controller" should {
    "create a valid procedure" in testApp { app =>
      val procedureDate = new Date()
      val inputProcedure = InputProcedure(
        "testProcedure2",
        procedureDate,
        "1",
        "T123"
      )

      val request = FakeRequest("POST", "/api/v1/procedure")
        .withJsonBody(Json.toJson(inputProcedure))
        .withHeaders("user" -> "admin@thehive.local")

      val result = app[ProcedureCtrl].create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultProcedure = contentAsJson(result).as[OutputProcedure]

      TestProcedure(resultProcedure) must_=== TestProcedure(
        "testProcedure2",
        procedureDate,
        "T123"
      )
    }
  }
}
