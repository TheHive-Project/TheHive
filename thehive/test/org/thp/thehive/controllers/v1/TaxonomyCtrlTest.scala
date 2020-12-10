package org.thp.thehive.controllers.v1

import org.thp.scalligraph.controllers.FakeTemporaryFile
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1._
import play.api.libs.Files
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.{FakeRequest, PlaySpecification}

case class TestTaxonomy(
  namespace: String,
  description: String,
  version: Int,
  tags: List[OutputTag]
)

object TestTaxonomy {
  def apply(outputTaxonomy: OutputTaxonomy): TestTaxonomy =
    TestTaxonomy(
      outputTaxonomy.namespace,
      outputTaxonomy.description,
      outputTaxonomy.version,
      outputTaxonomy.tags.toList
    )
}

class TaxonomyCtrlTest extends PlaySpecification with TestAppBuilder {
  "taxonomy controller" should {

    val inputTaxo = InputTaxonomy(
      "test-taxo",
      "A test taxonomy",
      1,
      None,
      None,
      List(
        InputPredicate("pred1", None, None, None),
        InputPredicate("pred2", None, None, None)
      ),
      Some(
        List(
          InputValue("pred1", List(InputEntry("entry1", None, None, None, None))),
          InputValue(
            "pred2",
            List(
              InputEntry("entry2", None, None, None, None),
              InputEntry("entry21", None, None, None, None)
            )
          )
        )
      )
    )

    "create a valid taxonomy" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy")
        .withJsonBody(Json.toJson(inputTaxo))
        .withHeaders("user" -> "admin@thehive.local")

      val result = app[TaxonomyCtrl].create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val resultCase = contentAsJson(result).as[OutputTaxonomy]

      TestTaxonomy(resultCase) must_=== TestTaxonomy(
        "test-taxo",
        "A test taxonomy",
        1,
        List(
          OutputTag("test-taxo", "pred1", Some("entry1"), None, 0),
          OutputTag("test-taxo", "pred2", Some("entry2"), None, 0),
          OutputTag("test-taxo", "pred2", Some("entry21"), None, 0)
        )
      )
    }

    "return error if not admin" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy")
        .withJsonBody(Json.toJson(inputTaxo))
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[TaxonomyCtrl].create(request)
      status(result) must beEqualTo(403).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("AuthorizationError")
    }

    "return error if namespace is present in database" in testApp { app =>
      val alreadyInDatabase = inputTaxo.copy(namespace = "taxonomy1")

      val request = FakeRequest("POST", "/api/v1/taxonomy")
        .withJsonBody(Json.toJson(alreadyInDatabase))
        .withHeaders("user" -> "admin@thehive.local")

      val result = app[TaxonomyCtrl].create(request)
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
      (contentAsJson(result) \ "message").as[String] must contain("already exists")
    }

    "return error if namespace is empty" in testApp { app =>
      val emptyNamespace = inputTaxo.copy(namespace = "")

      val request = FakeRequest("POST", "/api/v1/taxonomy")
        .withJsonBody(Json.toJson(emptyNamespace))
        .withHeaders("user" -> "admin@thehive.local")

      val result = app[TaxonomyCtrl].create(request)
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")

    }

    "get a taxonomy present" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = app[TaxonomyCtrl].get("taxonomy1")(request)
      status(result) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCase = contentAsJson(result).as[OutputTaxonomy]

      TestTaxonomy(resultCase) must_=== TestTaxonomy(
        "taxonomy1",
        "The taxonomy 1",
        1,
        List(OutputTag("taxonomy1", "pred1", Some("value1"), None, 0))
      )
    }

    "return error if taxonomy is not present in database" in testApp { app =>
      val request = FakeRequest("GET", "/api/v1/taxonomy/taxonomy404")
        .withHeaders("user" -> "admin@thehive.local")

      val result = app[TaxonomyCtrl].get("taxonomy404")(request)
      status(result) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("NotFoundError")
    }

    "import zip file correctly" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy/import-zip")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(multipartZipFile("machinetag.zip")))

      val result = app[TaxonomyCtrl].importZip(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsString(result) must not contain("Failure")
      contentAsJson(result).as[JsArray].value.size must beEqualTo(2)
    }

    "import zip file with folders correctly" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy/import-zip")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(multipartZipFile("machinetag-folders.zip")))

      val result = app[TaxonomyCtrl].importZip(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsString(result) must not contain("Failure")
      contentAsJson(result).as[JsArray].value.size must beEqualTo(2)
    }

    "return no error if zip file contains other files than taxonomies" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy/import-zip")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(multipartZipFile("machinetag-otherfiles.zip")))

      val result = app[TaxonomyCtrl].importZip(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsString(result) must not contain("Failure")
      contentAsJson(result).as[JsArray].value.size must beEqualTo(1)
    }

    "return error if zip file contains an already present taxonomy" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy/import-zip")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(multipartZipFile("machinetag-present.zip")))

      val result = app[TaxonomyCtrl].importZip(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsString(result) must contain("Failure")
      contentAsJson(result).as[JsArray].value.size must beEqualTo(2)
    }

    "return error if zip file contains a bad formatted taxonomy" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/taxonomy/import-zip")
        .withHeaders("user" -> "admin@thehive.local")
        .withBody(AnyContentAsMultipartFormData(multipartZipFile("machinetag-badformat.zip")))

      val result = app[TaxonomyCtrl].importZip(request)
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
      (contentAsJson(result) \ "message").as[String] must contain("formatting")
    }

    "activate a taxonomy" in testApp { app =>
      val request1 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy2")
        .withHeaders("user" -> "certuser@thehive.local")
      val result1 = app[TaxonomyCtrl].get("taxonomy2")(request1)
      status(result1) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("PUT", "/api/v1/taxonomy/taxonomy2")
        .withHeaders("user" -> "admin@thehive.local")
      val result2 = app[TaxonomyCtrl].toggleActivation("taxonomy2", isActive = true)(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy2")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = app[TaxonomyCtrl].get("taxonomy2")(request3)
      status(result3) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }

    "deactivate a taxonomy" in testApp { app =>
      val request1 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result1 = app[TaxonomyCtrl].get("taxonomy1")(request1)
      status(result1) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("PUT", "/api/v1/taxonomy/taxonomy1/deactivate")
        .withHeaders("user" -> "admin@thehive.local")
      val result2 = app[TaxonomyCtrl].toggleActivation("taxonomy1", isActive = false)(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = app[TaxonomyCtrl].get("taxonomy1")(request3)
      status(result3) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }

    "delete a taxonomy" in testApp { app =>
      val request1 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result1 = app[TaxonomyCtrl].get("taxonomy1")(request1)
      status(result1) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(result1)}")

      val request2 = FakeRequest("DELETE", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "admin@thehive.local")
      val result2 = app[TaxonomyCtrl].delete("taxonomy1")(request2)
      status(result2) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val request3 = FakeRequest("GET", "/api/v1/taxonomy/taxonomy1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result3 = app[TaxonomyCtrl].get("taxonomy1")(request3)
      status(result3) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(result3)}")
    }

  }

  def multipartZipFile(name: String): MultipartFormData[Files.TemporaryFile] =
    // file must be place in test/resources/
    MultipartFormData(
      dataParts = Map.empty,
      files = Seq(FilePart("file", name, Option("application/zip"), FakeTemporaryFile.fromResource(s"/$name"))),
      badParts = Seq()
    )

}
