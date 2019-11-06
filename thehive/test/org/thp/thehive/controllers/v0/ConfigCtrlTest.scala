package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class ConfigCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val configCtrl = app.instanceOf[ConfigCtrl]

    def getList = {
      val request = FakeRequest("GET", "/api/config")
        .withHeaders("X-Organisation" -> "admin", "user" -> "admin@thehive.local")
      val result = configCtrl.list(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[List[JsObject]]
    }

    s"$name config controller" should {
      "list configuration items" in {
        getList must not(beEmpty)
      }

      "set configuration item" in {
        val request = FakeRequest("PUT", "/api/config/tags.defaultColour")
          .withHeaders("X-Organisation" -> "admin", "user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""{"value": "#42"}"""))
        val result = configCtrl.set("tags.defaultColour")(request)

        status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

        val l = getList

        val v = l.find(o => (o \ "path").isDefined && (o \ "path").as[String] == "tags.defaultColour")

        v must beSome.which(defaultColour => (defaultColour \ "value").as[String] shouldEqual "#42")
      }

      "get user specific configuration" in {
        val request = FakeRequest("GET", "/api/config/user/organisation")
          .withHeaders("X-Organisation" -> "admin", "user" -> "admin@thehive.local")
        val result = configCtrl.userGet("organisation")(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "admin"
      }

      "set user specific configuration" in {
        val request = FakeRequest("PUT", "/api/config/user/organisation")
          .withHeaders("X-Organisation" -> "admin", "user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""{"value": "default"}"""))
        val result = configCtrl.userSet("organisation")(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "default"
      }
    }
  }
}
