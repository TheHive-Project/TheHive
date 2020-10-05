package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputProfile
import org.thp.thehive.models.Profile
import org.thp.thehive.services.ProfileSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

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
      val request = FakeRequest("PATCH", "/api/profile/testProfile")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"permissions": ["manageTask"]}"""))
      val result = app[ProfileCtrl].update("testProfile")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val updatedProfile = contentAsJson(result).as[OutputProfile]
      updatedProfile.permissions shouldEqual List("manageTask")
    }

    "fail to update non editable profile" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/profile/${Profile.orgAdmin.name}")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"permissions": ["manageTask"]}"""))
      val result = app[ProfileCtrl].update(Profile.orgAdmin.name)(request)

      status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "delete a profile if allowed" in testApp { app =>
      val request = FakeRequest("DELETE", "/api/profile/testProfile")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[ProfileCtrl].delete("testProfile")(request)
      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")
      app[Database].roTransaction { implicit graph =>
        app[ProfileSrv].get(EntityName("testProfile")).exists must beFalse
      }
    }

    "refuse to delete protected profile" in testApp { app =>
      val requestFailed = FakeRequest("DELETE", s"/api/profile/${Profile.orgAdmin.name}")
        .withHeaders("user" -> "admin@thehive.local")
      val resultFailed = app[ProfileCtrl].delete(Profile.orgAdmin.name)(requestFailed)

      status(resultFailed) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(resultFailed)}")
    }
  }
}
