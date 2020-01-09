package org.thp.thehive.controllers.v0

import org.thp.thehive.TestAppBuilder
import play.api.test.{FakeRequest, PlaySpecification}

class AttachmentCtrlTest extends PlaySpecification with TestAppBuilder {
  "download a simple attachment from his id" in testApp { app =>
    val request = FakeRequest("GET", "/api/v0/datastore/a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889")
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[AttachmentCtrl].download("a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889", None)(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    header("Content-Disposition", result) must beSome.which(
      h => h shouldEqual "attachment; filename=\"a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889\""
    )
  }

  "download a zipped attachment from his hash" in testApp { app =>
    val request = FakeRequest("GET", "/api/v0/datastorezip/a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889")
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[AttachmentCtrl].downloadZip("a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889", Some("lol"))(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    header("Content-Disposition", result) must beSome.which(
      h => h shouldEqual "attachment; filename=\"lol.zip\""
    )
  }
}
