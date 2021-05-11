package org.thp.thehive.controllers.v0

import akka.actor.ActorRef
import com.softwaremill.macwire.akkasupport.wireAnonymousActor
import com.softwaremill.tagging._
import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ConfigActor, ConfigTag}
import org.thp.thehive.services.WithTheHiveModule
import org.thp.thehive.{TestApplication, TheHiveModule}
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, PlaySpecification}

class ConfigCtrlTest extends PlaySpecification with TestAppBuilder {

  override def buildApp(db: Database): TestApplication with WithTheHiveModule with WithTheHiveModuleV0 =
    new TestApplication(db) with WithTheHiveModule with WithTheHiveModuleV0 {
      override lazy val configuration: Configuration = Configuration(
        ConfigFactory.parseString(
          """
            |auth.providers: [
            |  {name: header, userHeader: user}
            |  {name: local}
            |  {name: key}
            |]
            |""".stripMargin
        )
      ).withFallback(TestApplication.appWithoutDatabase.configuration)

      override lazy val configActor: ActorRef @@ ConfigTag =
        wireAnonymousActor[ConfigActor].taggedWith[ConfigTag]

      override val thehiveModule: TheHiveModule = buildTestModule(this)
      injectModule(thehiveModule)
      override val thehiveModuleV0: TheHiveModuleV0 = buildTestModuleV0(this)
      injectModule(thehiveModuleV0)
    }

  s"config controller" should {
    "list configuration items" in testApp { app =>
      import app.thehiveModuleV0._

      statusCtrl.tagsDefaultColourConfig.get

      val request = FakeRequest("GET", "/api/config")
        .withHeaders("user" -> "admin@thehive.local")
      val result = configCtrl.list(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[List[JsObject]] must not(beEmpty)
    }

    "set configuration item" in testApp { app =>
      import app.thehiveModule._
      import app.thehiveModuleV0._

      tagSrv.freeTagColour
      val request = FakeRequest("PUT", "/api/config/tags.freeTagColour")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"value": "#00FF00"}"""))
      val result = configCtrl.set("tags.freeTagColour")(request)

      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      tagSrv.freeTagColour must beEqualTo("#00FF00")

    }
// TODO leave unused tests ?
//
//      "get user specific configuration" in testApp { app =>
//import app._
//import app.testModule._
//import app.testModuleV0._
//
//        val request = FakeRequest("GET", "/api/config/user/organisation")
//          .withHeaders("user" -> "admin@thehive.local")
//        val result = configCtrl.userGet("organisation")(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "admin"
//      }
//
//      "set user specific configuration" in {
//        val request = FakeRequest("PUT", "/api/config/user/organisation")
//          .withHeaders("user" -> "admin@thehive.local")
//          .withJsonBody(Json.parse("""{"value": "default"}"""))
//        val result = configCtrl.userSet("organisation")(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//        (contentAsJson(result).as[JsObject] \ "value").as[String] shouldEqual "default"
//      }
  }
}
