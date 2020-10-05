package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0._
import org.thp.thehive.models.Profile
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class ShareCtrlTest extends PlaySpecification with TestAppBuilder {
  "share a case" in testApp { app =>
    val request = FakeRequest("POST", "/api/case/1/shares")
      .withJsonBody(Json.obj("shares" -> List(Json.toJson(InputShare("soc", Profile.orgAdmin.name, TasksFilter.all, ObservablesFilter.all)))))
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].shareCase("1")(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

    app[Database].roTransaction { implicit graph =>
      app[CaseSrv].get(EntityName("1")).visible(DummyUserSrv(organisation = "soc").authContext).exists
    } must beTrue
  }

  "fail to share a already share case" in testApp { app =>
    val request = FakeRequest("POST", "/api/case/2/shares")
      .withJsonBody(Json.obj("shares" -> Seq(Json.toJson(InputShare("soc", Profile.orgAdmin.name, TasksFilter.all, ObservablesFilter.all)))))
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].shareCase("2")(request)

    status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
  }

  "remove a share" in testApp { app =>
    val request = FakeRequest("DELETE", s"/api/case/2")
      .withJsonBody(Json.obj("organisations" -> Seq("soc")))
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].removeShares("2")(request)

    status(result) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")

    app[Database].roTransaction { implicit graph =>
      app[CaseSrv].get(EntityName("2")).visible(DummyUserSrv(userId = "socro@thehive.local").authContext).exists
    } must beFalse
  }

  "refuse to remove owner share" in testApp { app =>
    val request = FakeRequest("DELETE", s"/api/case/2")
      .withJsonBody(Json.obj("organisations" -> Seq("cert")))
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].removeShares("2")(request)

    status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
  }
}
