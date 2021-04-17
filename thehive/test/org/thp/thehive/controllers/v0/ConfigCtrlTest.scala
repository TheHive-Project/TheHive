//package org.thp.thehive.controllers.v0
//
//import org.thp.thehive.TestAppBuilder
//import org.thp.thehive.services.TagSrv
//import play.api.libs.json.{JsObject, Json}
//import play.api.test.{FakeRequest, PlaySpecification}
//
//class ConfigCtrlTest extends PlaySpecification with TestAppBuilder {
//
//// TODO leave unused code ?
////
////    def getList = {
////      val request = FakeRequest("GET", "/api/config")
////        .withHeaders("user" -> "admin@thehive.local")
////      val result = configCtrl.list(request)
////
////      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
////
////      contentAsJson(result).as[List[JsObject]]
////    }
//
//  s"config controller" should {
//    "list configuration items" in testApp { app =>
//      val request = FakeRequest("GET", "/api/config")
//        .withHeaders("user" -> "admin@thehive.local")
//      val result = app[ConfigCtrl].list(request)
//
//      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      contentAsJson(result).as[List[JsObject]] must not(beEmpty)
//    }
//
//    "set configuration item" in testApp { app =>
//      app[TagSrv]
//      val request = FakeRequest("PUT", "/api/config/tags.freeTagColour")
//        .withHeaders("user" -> "admin@thehive.local")
//        .withJsonBody(Json.parse("""{"value": "#00FF00"}"""))
//      val result = app[ConfigCtrl].set("tags.freeTagColour")(request)
//
//      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      app[TagSrv].freeTagColour must beEqualTo("#00FF00")
//    }
//// TODO leave unused tests ?
////
////      "get user specific configuration" in testApp { app =>
////        val request = FakeRequest("GET", "/api/config/user/organisation")
////          .withHeaders("user" -> "admin@thehive.local")
////        val result = app[ConfigCtrl].userGet("organisation")(request)
////
////        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
////        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "admin"
////      }
////
////      "set user specific configuration" in {
////        val request = FakeRequest("PUT", "/api/config/user/organisation")
////          .withHeaders("user" -> "admin@thehive.local")
////          .withJsonBody(Json.parse("""{"value": "default"}"""))
////        val result = app[ConfigCtrl].userSet("organisation")(request)
////
////        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
////        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "default"
////      }
//  }
//}
