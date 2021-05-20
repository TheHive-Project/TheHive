package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import play.api.libs.json.{JsBoolean, JsString}
import play.api.test.PlaySpecification

class ConfigSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "config service" should {
    "set/get values" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        configSrv.organisation.setConfigValue(EntityName("cert"), "test", JsBoolean(true))
        configSrv.user.setConfigValue(EntityName("certuser@thehive.local"), "test2", JsString("lol"))
      }

      database.roTransaction { implicit graph =>
        configSrv.organisation.getConfigValue(EntityName("cert"), "test")            must beSome.which(c => c.value.as[Boolean] must beTrue)
        configSrv.user.getConfigValue(EntityName("certuser@thehive.local"), "test2") must beSome.which(c => c.value.as[String] shouldEqual "lol")
      }
    }
  }
}
