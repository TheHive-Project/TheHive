package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl.TransformerOps

import org.thp.thehive.dto.v1.{InputProcedure, OutputProcedure}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

case class TestProcedure(
    description: Option[String],
    occurDate: Date,
    tactic: String,
    patternId: String
)

object TestProcedure {
  def apply(outputProcedure: OutputProcedure): TestProcedure =
    outputProcedure.into[TestProcedure].transform
}

class ProcedureCtrlTest extends PlaySpecification with TestAppBuilder {
  "procedure controller" should {
    "create a valid procedure" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV1._

      val procedureDate = new Date()
      val inputProcedure = InputProcedure(
        Some("testProcedure3"),
        procedureDate,
        "tactic1",
        "1",
        "T123"
      )

      val request = FakeRequest("POST", "/api/v1/procedure")
        .withJsonBody(Json.toJson(inputProcedure))
        .withHeaders("user" -> "certadmin@thehive.local")

      val result = procedureCtrl.create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultProcedure = contentAsJson(result).as[OutputProcedure]

      TestProcedure(resultProcedure) must_=== TestProcedure(
        Some("testProcedure3"),
        procedureDate,
        "tactic1",
        "T123"
      )
    }

    "update a procedure" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV1._

      val request1 = FakeRequest("POST", "/api/v1/procedure/testProcedure3")
        .withJsonBody(
          Json.toJson(
            InputProcedure(
              Some("an old description"),
              new Date(),
              "tactic1",
              "1",
              "T123"
            )
          )
        )
        .withHeaders("user" -> "certadmin@thehive.local")
      val result1     = procedureCtrl.create(request1)
      val procedureId = contentAsJson(result1).as[OutputProcedure]._id
      status(result1) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val updatedDate = new Date()
      val request2 = FakeRequest("PATCH", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(Json.obj("description" -> "a new description", "occurDate" -> updatedDate, "tactic" -> "tactic2"))
      val result2 = procedureCtrl.update(procedureId)(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result3 = procedureCtrl.get(procedureId)(request3)
      status(result3) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")

      val resultProcedure = contentAsJson(result3).as[OutputProcedure]
      TestProcedure(resultProcedure) must_=== TestProcedure(
        Some("a new description"),
        updatedDate,
        "tactic2",
        "T123"
      )
    }

    "delete a procedure" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV1._

      val request1 = FakeRequest("POST", "/api/v1/procedure/testProcedure3")
        .withJsonBody(
          Json.toJson(
            InputProcedure(
              Some("testProcedure3"),
              new Date(),
              "tactic1",
              "1",
              "T123"
            )
          )
        )
        .withHeaders("user" -> "certadmin@thehive.local")
      val result1     = procedureCtrl.create(request1)
      val procedureId = contentAsJson(result1).as[OutputProcedure]._id
      status(result1) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("DELETE", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result2 = procedureCtrl.delete(procedureId)(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/procedure/testProcedure3")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = procedureCtrl.get(procedureId)(request3)
      status(result3) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }
  }
}
