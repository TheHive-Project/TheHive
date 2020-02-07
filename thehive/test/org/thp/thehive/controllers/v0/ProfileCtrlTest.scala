package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputProfile

class ProfileCtrlTest extends PlaySpecification with TestAppBuilder {
  "profile controller" should {
    "create a profile if allowed" in testApp { app =>
      val request = FakeRequest("POST", "/api/profile")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse(s"""{"name": "name test 1", "permissions": ["manageCase"]}"""))
      val result = app[ProfileCtrl].create(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val profile = contentAsJson(result).as[OutputProfile]
      profile.name shouldEqual "name test 1"
      profile.permissions shouldEqual List("manageCase")
    }

    "get a profile" in testApp { app =>
      val request = FakeRequest("GET", s"/api/profile/read-only")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[ProfileCtrl].get("read-only")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[OutputProfile].name shouldEqual "read-only"
    }

    "update a profile" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/profile/testProfile")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"permissions": ["manageTask"]}"""))
      val result = app[ProfileCtrl].update("testProfile")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val updatedProfile = contentAsJson(result).as[OutputProfile]
      updatedProfile.permissions shouldEqual List("manageTask")
    }

    "fail to update non editable profile" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/profile/org-admin")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"permissions": ["manageTask"]}"""))
      val result = app[ProfileCtrl].update("org-admin")(request)

      status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "delete a profile if allowed" in testApp { app =>
      val request = FakeRequest("DELETE", s"/api/profile/testProfile")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[ProfileCtrl].delete("testProfile")(request)
      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      val requestGet = FakeRequest("GET", s"/api/profile/testProfile")
        .withHeaders("user" -> "admin@thehive.local")
      val resultGet = app[ProfileCtrl].get("testProfile")(requestGet)

      status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")

      val requestFailed = FakeRequest("DELETE", s"/api/profile/all")
        .withHeaders("user" -> "admin@thehive.local")
      val resultFailed = app[ProfileCtrl].delete("all")(requestFailed)

      status(resultFailed) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
    }
  }
}
