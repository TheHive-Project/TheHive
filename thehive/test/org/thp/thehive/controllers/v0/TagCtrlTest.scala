package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputTag
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

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
    val tagCtrl: TagCtrl     = app.instanceOf[TagCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    s"$name tag controller" should {
      "import taxonomy if allowed" in {
        val request = FakeRequest("POST", "/api/tag/_import")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse(s"""{
                                          "file": "",
                                         "namespace":"_autocreate",
                                         "values":[
                                            {
                                               "predicate":"test",
                                               "entry": [{
                                                "value": "lol",
                                                "colour": "#333",
                                                "description": "test"
                                               }]
                                            }
                                         ]
                                      }""".stripMargin))
        val result = tagCtrl.importTaxonomy(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      }.pendingUntilFixed("need more input")

      "get a tag" in {
        val request = FakeRequest("GET", "/api/tag/tagt1")
          .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "cert")
        val result = tagCtrl.get("tagt1")(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      }.pendingUntilFixed("need more input")

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
