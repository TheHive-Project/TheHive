package org.thp.thehive.services

import org.thp.scalligraph.{BadRequestError, EntityName}
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class LocalPasswordAuthSrvTest extends PlaySpecification with TestAppBuilder {
  "localPasswordAuth service" should {
    "be able to verify passwords" in testApp { app =>
      import app.thehiveModule._
      import app.{configuration, database}
      import com.softwaremill.macwire._

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

    "be able to verify passwords policy" in testApp { app =>
      import app.configuration
      import com.softwaremill.macwire._

      val localPasswordAuthProvider = wire[LocalPasswordAuthProvider]
      implicit val authCtx          = LocalUserSrv.getSystemAuthContext

      {
        val policyConfig         = Configuration("passwordPolicy.enabled" -> true, "passwordPolicy.minLength" -> 12)
        val localPasswordAuthSrv = localPasswordAuthProvider.apply(policyConfig withFallback configuration).get.asInstanceOf[LocalPasswordAuthSrv]

        val result = localPasswordAuthSrv.setPassword("foo", "foo")
        result                       must beFailedTry.withThrowable[BadRequestError]
        result.failed.get.getMessage must contain("Password must be 12 or more characters in length")
      }

      {
        val policyConfig         = Configuration("passwordPolicy.enabled" -> true, "passwordPolicy.minUpperCase" -> 1)
        val localPasswordAuthSrv = localPasswordAuthProvider.apply(policyConfig withFallback configuration).get.asInstanceOf[LocalPasswordAuthSrv]

        val result = localPasswordAuthSrv.setPassword("foo", "foo")
        result                       must beFailedTry.withThrowable[BadRequestError]
        result.failed.get.getMessage must contain("Password must contain 1 or more uppercase characters")
      }

      {
        val policyConfig = Configuration(
          "passwordPolicy.enabled"               -> true,
          "passwordPolicy.minSpecial"            -> 1,
          "passwordPolicy.cannotContainUsername" -> true
        )
        val localPasswordAuthSrv = localPasswordAuthProvider.apply(policyConfig withFallback configuration).get.asInstanceOf[LocalPasswordAuthSrv]

        val result = localPasswordAuthSrv.setPassword("foo", "foo123")
        result                       must beFailedTry.withThrowable[BadRequestError]
        result.failed.get.getMessage must contain("Password must contain 1 or more special characters")
        result.failed.get.getMessage must contain("Password contains the user id 'foo'")
      }
    }
  }
}
