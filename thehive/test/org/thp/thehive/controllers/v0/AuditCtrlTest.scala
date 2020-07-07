package org.thp.thehive.controllers.v0

import java.util.Date

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Case, CaseStatus, Permissions}
import org.thp.thehive.services.{CaseSrv, OrganisationSrv}
import play.api.libs.json.JsObject
import play.api.test.{FakeRequest, PlaySpecification}

class AuditCtrlTest extends PlaySpecification with TestAppBuilder {
  val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  "return a list of audits including the last created one" in testApp { app =>
    def getFlow(caseId: String) = {
      val request = FakeRequest("GET", "/api/v0/flow")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[AuditCtrl].flow(Some(caseId))(request)
      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[List[JsObject]]
    }

    // Check for no parasite audit
    getFlow("#1") must beEmpty

    // Create an event first
    val `case` = app[Database].tryTransaction { implicit graph =>
      app[CaseSrv].create(
        Case(0, "case audit", "desc audit", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
        None,
        app[OrganisationSrv].getOrFail("admin").get,
        Set.empty,
        Map.empty,
        None,
        Nil
      )(graph, authContext)
    }.get

    // Get the actual data
    val l = getFlow(`case`._id)

//    l must not(beEmpty)
    pending
  }
}
