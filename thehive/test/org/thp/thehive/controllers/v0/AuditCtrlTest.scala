package org.thp.thehive.controllers.v0

import akka.actor.ActorRef
import org.thp.scalligraph.{EntityIdOrName, ScalligraphApplication}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.{Case, CaseStatus, Permissions}
import org.thp.thehive.services.{FlowActor, FlowTag, TheHiveTestModule}
import play.api.libs.json.JsObject
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

class AuditCtrlTest extends PlaySpecification with TestAppBuilder {
  val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext

  override def buildTestModule(app: ScalligraphApplication): TheHiveModule =
    new TheHiveTestModule(app) {
      import com.softwaremill.macwire.akkasupport._
      import com.softwaremill.tagging._
      import app._

      override lazy val flowActor: ActorRef @@ FlowTag = wireActor[FlowActor]("flow-actor").taggedWith[FlowTag]
    }

  "return a list of audits including the last created one" in testApp { app =>
    import app._
    import app.thehiveModule._
    import app.thehiveModuleV0._

    def getFlow(caseId: String) = {
      val request = FakeRequest("GET", "/api/v0/flow")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = auditCtrl.flow(Some(caseId))(request)
      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[List[JsObject]]
    }

    // Check for no parasite audit
    getFlow("1") must beEmpty

    // Create an event first
    val `case` = database.tryTransaction { implicit graph =>
      val organisation = organisationSrv.getOrFail(EntityIdOrName("admin")).get
      caseSrv.create(
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
