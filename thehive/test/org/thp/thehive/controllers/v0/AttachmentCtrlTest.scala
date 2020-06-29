package org.thp.thehive.controllers.v0

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services.AttachmentSrv
import play.api.test.{FakeRequest, PlaySpecification}

class AttachmentCtrlTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").authContext
  val data: String                      = "DataFromAttachmentCtrlTest"
  "download a simple attachment from his id" in testApp { app =>
    app[Database].tryTransaction { implicit graph =>
      app[AttachmentSrv].create("test.txt", data.length.toLong, "text/plain", Source.single(ByteString(data)))
    }
    val request = FakeRequest("GET", "/api/v0/datastore/810384dd79918958607f6a6e4c90f738c278c847b408864ea7ce84ee1970bcdf")
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[AttachmentCtrl].download("810384dd79918958607f6a6e4c90f738c278c847b408864ea7ce84ee1970bcdf", None)(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    header("Content-Disposition", result) must beSome("attachment; filename=\"810384dd79918958607f6a6e4c90f738c278c847b408864ea7ce84ee1970bcdf\"")
  }

  "download a zipped attachment from his hash" in testApp { app =>
    app[Database].tryTransaction { implicit graph =>
      app[AttachmentSrv].create("test.txt", data.length.toLong, "text/plain", Source.single(ByteString(data)))
    }
    val request = FakeRequest("GET", "/api/v0/datastorezip/810384dd79918958607f6a6e4c90f738c278c847b408864ea7ce84ee1970bcdf")
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[AttachmentCtrl].downloadZip("810384dd79918958607f6a6e4c90f738c278c847b408864ea7ce84ee1970bcdf", Some("lol"))(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    header("Content-Disposition", result) must beSome("attachment; filename=\"lol.zip\"")
  }
}
