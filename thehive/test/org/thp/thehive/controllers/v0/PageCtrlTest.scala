package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputPage
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class PageCtrlTest extends PlaySpecification with Mockito {
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
    val pageCtrl: PageCtrl   = app.instanceOf[PageCtrl]
    val theHiveQueryExecutor = app.instanceOf[TheHiveQueryExecutor]

    def createPage(title: String, content: String) = {
      val request = FakeRequest("POST", "/api/page")
        .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.parse(s"""{"title": "$title", "content": "$content"}"""))
      val result = pageCtrl.create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[OutputPage]
    }

    s"$name page controller" should {
      "create a simple page if allowed" in {
        val page = createPage("test title", "test content")

        page.title shouldEqual "test title"
        page.content shouldEqual "test content"

        val requestFailed = FakeRequest("POST", "/api/page")
          .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse("""{"title": "", "content": ""}"""))
        val resultFailed = pageCtrl.create(requestFailed)

        status(resultFailed) must equalTo(403).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
      }

      "get a page by id or title" in {
        val page = createPage("test title 2", "test content 2")

        val request = FakeRequest("GET", s"/api/page/${page.id}")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "cert")
        val result = pageCtrl.get(page.id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val resultPage = contentAsJson(result).as[OutputPage]

        resultPage.title shouldEqual page.title
        resultPage.content shouldEqual page.content

        val requestFailed = FakeRequest("GET", s"/api/page/${page.id}")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        val resultFailed = pageCtrl.get(page.id)(requestFailed)

        status(resultFailed) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")

        val requestTitle = FakeRequest("GET", s"/api/page/${page.title}")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "cert")
        val resultTitle = pageCtrl.get(page.title)(requestTitle)

        status(resultTitle) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultTitle)}")
      }

      "update a page if allowed" in {
        val page = createPage("test title 3", "test content 3")

        val request = FakeRequest("PATCH", s"/api/page/${page.title}")
          .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(Json.parse("""{"title": "lol"}"""))
        val result = pageCtrl.update(page.title)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val r = contentAsJson(result).as[OutputPage]

        r.title shouldEqual "lol"
        r.content shouldEqual "test content 3"
      }

      "remove a page" in {
        val page = createPage("test title 4", "test content 4")

        val request = FakeRequest("DELETE", s"/api/page/${page.title}")
          .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
        val result = pageCtrl.delete(page.title)(request)

        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

        val requestFailed = FakeRequest("GET", s"/api/page/${page.id}")
          .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
        val resultFailed = pageCtrl.get(page.id)(requestFailed)

        status(resultFailed) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
      }

      "search a page" in {
        createPage("test title 5", "test content 5")
        createPage("test title 6", "test content 6")
        createPage("test title 7", "test content 7")
        val json = Json.parse("""{
             "range":"all",
             "sort":[
                "-updatedAt",
                "-createdAt"
             ],
             "query":{
                "_and":[
                   {
                      "_not":{
                         "title":"test title 7"
                      }
                   },
                   {
                      "_or":[
                         {
                            "content":"test content 5"
                         },
                         {
                            "title":"test title 6"
                         }
                      ]
                   }
                ]
             }
          }""".stripMargin)

        val request = FakeRequest("POST", s"/api/page/_search")
          .withHeaders("user" -> "user5@thehive.local", "X-Organisation" -> "cert")
          .withJsonBody(json)
        val result = theHiveQueryExecutor.page.search(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        contentAsJson(result).as[List[OutputPage]].length shouldEqual 2
      }
    }
  }
}
