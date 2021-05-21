package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.dto.v0._
import org.thp.thehive.models.Profile
import org.thp.thehive.services.TheHiveOps
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import eu.timepit.refined.auto._
import org.thp.thehive.dto.String64

class ShareCtrlTest extends PlaySpecification with TestAppBuilder {
  "share a case" in testApp { app =>
    import app._
    import app.thehiveModule._
    import app.thehiveModuleV0._

    TheHiveOps(organisationSrv, customFieldSrv) { ops =>
      import ops._

      val request = FakeRequest("POST", "/api/case/1/shares")
        .withJsonBody(
          Json.obj(
            "shares" -> List(Json.toJson(InputShare("soc", String64("profileName", Profile.orgAdmin.name), TasksFilter.all, ObservablesFilter.all)))
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = shareCtrl.shareCase("1")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      database.roTransaction { implicit graph =>
        caseSrv.get(EntityName("1")).visible(DummyUserSrv(organisation = "soc").authContext).exists
      } must beTrue
    }
  }

  "fail to share a already share case" in testApp { app =>
    import app.thehiveModuleV0._

    val request = FakeRequest("POST", "/api/case/2/shares")
      .withJsonBody(
        Json.obj(
          "shares" -> Seq(Json.toJson(InputShare("soc", String64("profileName", Profile.orgAdmin.name), TasksFilter.all, ObservablesFilter.all)))
        )
      )
      .withHeaders("user" -> "certuser@thehive.local")
    val result = shareCtrl.shareCase("2")(request)

    status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
  }

  "remove a share" in testApp { app =>
    import app._
    import app.thehiveModule._
    import app.thehiveModuleV0._

    TheHiveOps(organisationSrv, customFieldSrv) { ops =>
      import ops._

      val request = FakeRequest("DELETE", s"/api/case/2")
        .withJsonBody(Json.obj("organisations" -> Seq("soc")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = shareCtrl.removeShares("2")(request)

      status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

      database.roTransaction { implicit graph =>
        caseSrv.get(EntityName("2")).visible(DummyUserSrv(organisation = "soc").authContext).exists
      } must beFalse
    }
  }

  "refuse to remove owner share" in testApp { app =>
    import app.thehiveModuleV0._

    val request = FakeRequest("DELETE", s"/api/case/2")
      .withJsonBody(Json.obj("organisations" -> Seq("cert")))
      .withHeaders("user" -> "certuser@thehive.local")
    val result = shareCtrl.removeShares("2")(request)

    status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
  }
}
