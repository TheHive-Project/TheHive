package org.thp.thehive.controllers.v0

import java.io.File
import java.nio.file.{Path, Files => JFiles}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputTag
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services.TagSrv
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Headers, MultipartFormData}
import play.api.test.{FakeRequest, NoMaterializer, NoTemporaryFileCreator, PlaySpecification}

import scala.util.Try

class TagCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val tagSrv: TagSrv       = app.instanceOf[TagSrv]
    val tagCtrl: TagCtrl     = app.instanceOf[TagCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]
    val db                   = app.instanceOf[Database]

    s"$name tag controller" should {
      "import a taxonomy json if allowed" in {
        val request = FakeRequest("POST", "/api/tag/_import")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "cert")
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
        val result = tagCtrl.importTaxonomy(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsString(result) shouldEqual "2"
      }

      "import a taxonomy file if allowed" in {
        WithFakeTaxonomyFile { tempFile =>
          val files = Seq(FilePart("file", "machinetag.json", Some("application/json"), tempFile))
          val request = FakeRequest(
            "POST",
            "/api/tag/_import",
            Headers("user" -> "user3@thehive.local", "X-Organisation" -> "cert"),
            body = AnyContentAsMultipartFormData(MultipartFormData(Map.empty, files, Nil))
          )
          val result = tagCtrl.importTaxonomy(request)

          status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
          contentAsString(result) shouldEqual "1"
        }
      }

      "get a tag" in {
        // Get a tag id first
        val tags = db.roTransaction(implicit graph => tagSrv.initSteps.toList)
        val tag = tags.head

        val request = FakeRequest("GET", s"/api/tag/${tag._id}")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "cert")
        val result = tagCtrl.get(tag._id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      }

      "search a tag" in {
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
          .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(json)
        val result = theHiveQueryExecutor.tag.search(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val l = contentAsJson(result).as[List[OutputTag]]

        l.length shouldEqual 2
        l.find(_.value.get == "testDomain") must beSome
      }
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
                                    "value": "password-guessing",
                                    "expanded": "Password guessing",
                                    "description": "Access was gained through guessing passwords through trial and error."
                                  }
                                ]
                              }""".stripMargin.getBytes
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
