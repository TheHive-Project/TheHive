package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.models.Database
import org.thp.thehive.TestAppBuilder
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class LocalPasswordAuthSrvTest extends PlaySpecification with TestAppBuilder {
  "localPasswordAuth service" should {
    "be able to verify passwords" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val certuser             = app[UserSrv].getOrFail(EntityName("certuser@thehive.local")).get
        val localPasswordAuthSrv = app[LocalPasswordAuthProvider].apply(app[Configuration]).get.asInstanceOf[LocalPasswordAuthSrv]
        val request = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(
            Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}""")
          )

        localPasswordAuthSrv.authenticate(certuser.login, "my-secret-password", None, None)(request) must beSuccessfulTry
      }
    }
  }
}
