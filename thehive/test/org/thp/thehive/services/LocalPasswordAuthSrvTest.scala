package org.thp.thehive.services

import org.thp.scalligraph.EntityName

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class LocalPasswordAuthSrvTest extends PlaySpecification with TestAppBuilder {
  "localPasswordAuth service" should {
    "be able to verify passwords" in testApp { app =>
      import com.softwaremill.macwire._
      import app.{configuration, database}
      import app.thehiveModule._

      val localPasswordAuthProvider = wire[LocalPasswordAuthProvider]

      database.roTransaction { implicit graph =>
        val certuser             = userSrv.getOrFail(EntityName("certuser@thehive.local")).get
        val localPasswordAuthSrv = localPasswordAuthProvider.apply(configuration).get.asInstanceOf[LocalPasswordAuthSrv]
        val request = FakeRequest("POST", "/api/v0/login")
          .withJsonBody(
            Json.parse("""{"user": "certuser@thehive.local", "password": "my-secret-password"}""")
          )

        localPasswordAuthSrv.authenticate(certuser.login, "my-secret-password", None, None)(request) must beSuccessfulTry
      }
    }
  }
}
