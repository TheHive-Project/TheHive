package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import play.api.libs.json.{JsBoolean, JsString}
import play.api.test.PlaySpecification

class ConfigSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "config service" should {
    "set/get values" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[ConfigSrv].organisation.setConfigValue(EntityName("cert"), "test", JsBoolean(true))
        app[ConfigSrv].user.setConfigValue(EntityName("certuser@thehive.local"), "test2", JsString("lol"))
      }

      app[Database].roTransaction { implicit graph =>
        app[ConfigSrv].organisation.getConfigValue(EntityName("cert"), "test") must beSome.which(c => c.value.as[Boolean] must beTrue)
        app[ConfigSrv].user.getConfigValue(EntityName("certuser@thehive.local"), "test2") must beSome.which(c => c.value.as[String] shouldEqual "lol")
      }
    }
  }
}
