package org.thp.thehive.controllers.v0

import java.util.Date

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.{AppBuilder, EntityIdOrName}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Case, CaseStatus, Permissions}
import org.thp.thehive.services.{CaseSrv, FlowActor, OrganisationSrv}
import play.api.libs.json.JsObject
import play.api.test.{FakeRequest, PlaySpecification}

class AuditCtrlTest extends PlaySpecification with TestAppBuilder {
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .`override`(_.bindActor[FlowActor]("flow-actor"))

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
    getFlow("1") must beEmpty

    // Create an event first
    val `case` = app[Database].tryTransaction { implicit graph =>
      val organisation = app[OrganisationSrv].getOrFail(EntityIdOrName("admin")).get
      app[CaseSrv].create(
        `case` = Case(
          title = "case audit",
          description = "desc audit",
          severity = 1,
          startDate = new Date,
          endDate = None,
          flag = false,
          tlp = 1,
          pap = 1,
          status = CaseStatus.Open,
          summary = None,
          tags = Nil
        ),
        assignee = None,
        organisation = organisation,
        customFields = Nil,
        caseTemplate = None,
        additionalTasks = Nil
      )(graph, authContext)
    }.get

    // Get the actual data
    val l = getFlow(`case`._id.toString)

//    l must not(beEmpty)
    pending
  }
}
