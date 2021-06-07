package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.models._
import play.api.libs.json._
import play.api.test.PlaySpecification

class CustomFieldSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "custom field service" should {
    "create a custom field" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        customFieldSrv.create(
          CustomField(
            "cf 1",
            "displayed cf 1",
            "desc cf 1",
            CustomFieldBoolean,
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
        import app._
        import app.thehiveModule._

        database.tryTransaction { implicit graph =>
          for {
            cf <- customFieldSrv.getOrFail(EntityName("boolean1"))
            _  <- customFieldSrv.delete(cf, force = true)
          } yield ()
        } must beSuccessfulTry
      }

      "count use of custom fields" in testApp { app =>
        import app._
        import app.thehiveModule._

        database.roTransaction { implicit graph =>
          customFieldSrv.useCount(customFieldSrv.getOrFail(EntityName("boolean1")).get)
        } shouldEqual Map("Case" -> 1, "CaseTemplate" -> 1)
      }
    }
  }
}
