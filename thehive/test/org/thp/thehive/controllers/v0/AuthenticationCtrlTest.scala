package org.thp.thehive.controllers.v0

import org.thp.thehive.dto.v0.OutputUser
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class AuthenticationCtrlTest extends PlaySpecification with TestAppBuilder {
  "login and logout users" in testApp { app =>
    import app.thehiveModuleV0._

    val request = FakeRequest("POST", "/api/v0/login")
      .withJsonBody(Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}"""))
    val result = authenticationCtrl.login(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    contentAsJson(result).as[OutputUser].name shouldEqual "certuser"

    val requestOut = FakeRequest("GET", "/api/v0/logout")
    val resultOut  = authenticationCtrl.logout()(requestOut)

    status(resultOut) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
  }
}
