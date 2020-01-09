package org.thp.thehive.services

import play.api.libs.json._
import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class CustomFieldSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "custom field service" should {
    "create a custom field" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[CustomFieldSrv].create(
          CustomField(
            "cf 1",
            "displayed cf 1",
            "desc cf 1",
            CustomFieldType.boolean,
            mandatory = false,
            Seq(JsBoolean(true))
          )
        )
      } must beSuccessfulTry.which { cf =>
        cf.name shouldEqual "cf 1"
        cf.displayName shouldEqual "displayed cf 1"
        cf.description shouldEqual "desc cf 1"
        cf.options shouldEqual Seq(JsBoolean(true))
      }

      "delete custom fields" in testApp { app =>
        app[Database].tryTransaction { implicit graph =>
          for {
            cf <- app[CustomFieldSrv].getOrFail("boolean1")
            _  <- app[CustomFieldSrv].delete(cf, force = true)
          } yield ()
        } must beSuccessfulTry
      }

      "count use of custom fields" in testApp { app =>
        app[Database].roTransaction { implicit graph =>
          app[CustomFieldSrv].useCount(app[CustomFieldSrv].getOrFail("boolean1").get)
        } shouldEqual Map("Case" -> 1, "CaseTemplate" -> 1)
      }
    }
  }
}
