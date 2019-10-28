package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.test.{FakeRequest, PlaySpecification}

import scala.util.Try

class AttachmentCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val attachmentCtrl: AttachmentCtrl      = app.instanceOf[AttachmentCtrl]
    implicit val materializer: Materializer = app.instanceOf[Materializer]

    "download a simple attachment from his id" in {
      val request = FakeRequest("GET", "/api/v0/datastore/a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val result = attachmentCtrl.download("a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889", None)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      header("Content-Disposition", result) must beSome.which(
        h => h shouldEqual "attachment; filename=\"a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889\""
      )
    }

    "download a zipped attachment from his hash" in {
      val request = FakeRequest("GET", "/api/v0/datastorezip/a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val result = attachmentCtrl.downloadZip("a4bf1f6be616bf6a0de2ff6264de43a64bb768d38c783ec2bc74b5d4dcf5f889", Some("lol"))(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      header("Content-Disposition", result) must beSome.which(
        h => h shouldEqual "attachment; filename=\"lol.zip\""
      )
    }
  }
}
