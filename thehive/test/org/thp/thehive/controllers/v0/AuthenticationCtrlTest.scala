//package org.thp.thehive.controllers.v0
//
//import org.thp.thehive.TestAppBuilder
//import org.thp.thehive.dto.v0.OutputUser
//import play.api.libs.json.Json
//import play.api.test.{FakeRequest, PlaySpecification}
//
//class AuthenticationCtrlTest extends PlaySpecification with TestAppBuilder {
//  "login and logout users" in testApp { app =>
//    val request = FakeRequest("POST", "/api/v0/login")
//      .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
//    val result = app[AuthenticationCtrl].login(request)
//
//    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//    contentAsJson(result).as[OutputUser].name shouldEqual "certuser"
//
//    val requestOut = FakeRequest("GET", "/api/v0/logout")
//    val resultOut  = app[AuthenticationCtrl].logout()(requestOut)
//
//    status(resultOut) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//  }
//}
