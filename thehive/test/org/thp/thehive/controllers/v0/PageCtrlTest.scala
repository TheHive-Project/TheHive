package org.thp.thehive.controllers.v0

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputPage
import org.thp.thehive.services.PageSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class PageCtrlTest extends PlaySpecification with TestAppBuilder {
//  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
//    def createPage(title: String, content: String, order: Int, slug: String, cat: String) = {
//      val request = FakeRequest("POST", "/api/page")
//        .withHeaders("user" -> "certuser@thehive.local")
//        .withJsonBody(Json.parse(s"""{"title": "$title", "content": "$content", "slug": "$slug", "category": "$cat", "order": $order}"""))
//      val result = app[PageCtrl].create(request)
//
//      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      contentAsJson(result).as[OutputPage]
//    }

  s"page controller" should {
    "create a simple page if allowed" in testApp { app =>
      val request = FakeRequest("POST", "/api/page")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(Json.parse(s"""{"title": "test title", "content": "test content", "category": "test cat", "order": 0}"""))
      val result = app[PageCtrl].create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val page = contentAsJson(result).as[OutputPage]

      page.title shouldEqual "test title"
      page.content shouldEqual "test content"
      page.slug shouldEqual "test_title"
      page.category shouldEqual "test cat"
      page.order shouldEqual 0
    }

    "get a page by id or title" in testApp { app =>
      val request = FakeRequest("GET", s"/api/page/how_to_create_a_case")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[PageCtrl].get("how_to_create_a_case")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultPage = contentAsJson(result).as[OutputPage]
      resultPage.title shouldEqual "how to create a case"
      resultPage.content shouldEqual "this page explain how to create a case"
    }

    "update a page if allowed" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/page/how_to_create_a_case")
        .withHeaders("user" -> "certadmin@thehive.local")
        .withJsonBody(Json.parse("""{"title": "lol"}"""))
      val result = app[PageCtrl].update("how_to_create_a_case")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val r = contentAsJson(result).as[OutputPage]
      r.title shouldEqual "lol"
      r.content shouldEqual "this page explain how to create a case"
    }

    "remove a page" in testApp { app =>
      val request = FakeRequest("DELETE", s"/api/page/how_to_create_a_case")
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = app[PageCtrl].delete("how_to_create_a_case")(request)
      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      app[Database].roTransaction { implicit graph =>
        app[PageSrv].get("how_to_create_a_case").exists()
      } must beFalse
    }
  }
}
