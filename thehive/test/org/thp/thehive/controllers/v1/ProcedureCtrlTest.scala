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
        "testProcedure3",
        procedureDate,
        "1",
        "T123"
      )

      val request = FakeRequest("POST", "/api/v1/procedure")
        .withJsonBody(Json.toJson(inputProcedure))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = app[ProcedureCtrl].create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultProcedure = contentAsJson(result).as[OutputProcedure]

      TestProcedure(resultProcedure) must_=== TestProcedure(
        "testProcedure3",
        procedureDate,
        "T123"
      )
    }

    // TODO test update of fields
    //   description

    "delete a procedure" in testApp { app =>
      val request1 = FakeRequest("POST", "/api/v1/procedure/testProcedure3")
        .withJsonBody(
          Json.toJson(
            InputProcedure(
              "testProcedure3",
              new Date(),
              "1",
              "T123"
            )
          )
        )
        .withHeaders("user" -> "certadmin@thehive.local")
      val result1     = app[ProcedureCtrl].create(request1)
      val procedureId = contentAsJson(result1).as[OutputProcedure]._id
      status(result1) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("DELETE", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result2 = app[ProcedureCtrl].delete(procedureId)(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = app[ProcedureCtrl].get(procedureId)(request3)
      status(result3) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }
  }
}
