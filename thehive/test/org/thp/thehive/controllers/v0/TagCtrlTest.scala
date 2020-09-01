package org.thp.thehive.controllers.v0

import java.io.File
import java.nio.file.{Path, Files => JFiles}

import akka.stream.Materializer
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputTag
import org.thp.thehive.services.TagSrv
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Headers, MultipartFormData}
import play.api.test.{FakeRequest, NoTemporaryFileCreator, PlaySpecification}

class TagCtrlTest extends PlaySpecification with TestAppBuilder {
  "tag controller" should {
    "import a taxonomy json" in testApp { app =>
      val request = FakeRequest("POST", "/api/tag/_import")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse(s"""{
          "content": {
            "namespace": "access-method",
            "description": "The access method used to remotely access a system.",
            "version": 1,
            "expanded": "Access method",
            "predicates": [
              {
                "value": "brute-force",
                "expanded": "Brute force",
                "description": "Access was gained through systematic trial of credentials in bulk."
              },
              {
                "value": "password-guessing",
                "expanded": "Password guessing",
                "description": "Access was gained through guessing passwords through trial and error."
              }
            ]
          }
          }""".stripMargin))
      val result = app[TagCtrl].importTaxonomy(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsString(result) shouldEqual "2"
    }

    "import a taxonomy json with value" in testApp { app =>
      val request = FakeRequest("POST", "/api/tag/_import")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse(s"""{
           "content":{
              "namespace":"dark-web",
              "expanded":"Dark Web",
              "description":"Criminal motivation on the dark web: A categorisation model for law enforcement.",
              "version":3,
              "predicates":[
                 {
                    "value":"topic",
                    "description":"Topic associated with the materials tagged",
                    "expanded":"Topic"
                 },
                 {
                    "value":"topic",
                    "description":"Topic associated with the materials tagged",
                    "expanded":"Topic"
                 },
                 {
                    "value":"structure",
                    "description":"Structure of the materials tagged",
                    "expanded":"Structure"
                 }
              ],
              "values":[
                 {
                    "predicate":"topic",
                    "entry":[
                       {
                          "value":"drugs-narcotics",
                          "expanded":"Drugs/Narcotics",
                          "description":"Illegal drugs/chemical compounds for consumption/ingestion..."
                       },
                       {
                          "value":"drugs-narcotics",
                          "expanded":"Drugs/Narcotics",
                          "description":"Illegal drugs/chemical compounds for consumption/ingestion..."
                       }
                    ]
                 }
              ]
           }
            }""".stripMargin))
      val result = app[TagCtrl].importTaxonomy(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsString(result) shouldEqual "1"
    }

    "import a taxonomy file if not existing" in testApp { app =>
      WithFakeTaxonomyFile { tempFile =>
        val files = Seq(FilePart("file", "machinetag.json", Some("application/json"), tempFile))
        val request = FakeRequest(
          "POST",
          "/api/tag/_import",
          Headers("user" -> "admin@thehive.local"),
          body = AnyContentAsMultipartFormData(MultipartFormData(Map.empty, files, Nil))
        )
        val result = app[TagCtrl].importTaxonomy(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsString(result) shouldEqual "3"

        val resultBis = app[TagCtrl].importTaxonomy(request)

        status(resultBis) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultBis)}")
        contentAsString(resultBis) shouldEqual "0"
      }
    }

    "get a tag" in testApp { app =>
      // Get a tag id first
      val tags = app[Database].roTransaction(implicit graph => app[TagSrv].startTraversal.toSeq)
      val tag  = tags.head

      val request = FakeRequest("GET", s"/api/tag/${tag._id}")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[TagCtrl].get(tag._id)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "search a tag" in testApp { app =>
      val json = Json.parse("""{
         "range":"all",
         "sort":[
            "-updatedAt",
            "-createdAt"
         ],
         "query":{
            "_and":[
               {
                  "_is":{
                     "namespace":"testNamespace"
                  }
               },
               {
                  "_or":[
                     {
                        "value":"testDomain"
                     },
                     {
                        "value":"hello"
                     }
                  ]
               }
            ]
         }
          }""".stripMargin)

      val request = FakeRequest("POST", s"/api/tag/_search")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(json)
      val result = app[TheHiveQueryExecutor].tag.search(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val l = contentAsJson(result)(defaultAwaitTimeout, app[Materializer]).as[List[OutputTag]]

      l.length shouldEqual 2
      l.find(_.value.get == "testDomain") must beSome
    }
  }
}

object WithFakeTaxonomyFile {

  def apply[A](body: Files.TemporaryFile => A): A = {
    val tempFile = JFiles.createTempFile("thehive-", "-test.json")
    JFiles.write(
      tempFile,
      s"""{
        "namespace": "access-method",
        "description": "The access method used to remotely access a system.",
        "version": 1,
        "expanded": "Access method",
        "predicates": [
          {
            "value": "password-guessing2",
            "expanded": "Password guessing",
            "description": "Access was gained through guessing passwords through trial and error."
          },
          {
            "value": "password-guessing",
            "expanded": "Password guessing",
            "description": "Access was gained through guessing passwords through trial and error."
          },
          {
            "value": "brute-force2",
            "expanded": "Brute forcing",
            "description": "Yeah..."
          }
        ]
        }""".getBytes
    )
    val fakeTempFile = new Files.TemporaryFile {
      override def path: Path                                 = tempFile
      override def file: File                                 = tempFile.toFile
      override def temporaryFileCreator: TemporaryFileCreator = NoTemporaryFileCreator
    }
    try body(fakeTempFile)
    finally {
      JFiles.deleteIfExists(tempFile)
      ()
    }
  }
}
