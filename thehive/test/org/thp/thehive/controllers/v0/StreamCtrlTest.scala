package org.thp.thehive.controllers.v0

import java.util.Date

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv}
import play.api.test.{FakeRequest, PlaySpecification}

class StreamCtrlTest extends PlaySpecification with TestAppBuilder {
  "stream controller" should {
    "create a stream" in testApp { app =>
      val request = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[StreamCtrl].create(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsString(result) must not(beEmpty)
    }

    "get a case related stream" in testApp { app =>
      implicit val authContext: AuthContext = DummyUserSrv(permissions = Permissions.all).authContext

      val createStreamRequest = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val createStreamResult = app[StreamCtrl].create(createStreamRequest)

      status(createStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(createStreamResult)}")
      val streamId = contentAsString(createStreamResult)

      // Add an event
      app[Database].tryTransaction { implicit graph =>
        val organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get
        app[CaseSrv].create(
          Case(
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
        )
      } must beASuccessfulTry

      val getStreamRequest = FakeRequest("GET", s"/api/stream/$streamId")
        .withHeaders("user" -> "certuser@thehive.local")
      val getStreamResult = app[StreamCtrl].get(streamId)(getStreamRequest)

      status(getStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(getStreamResult)}")
      val stream = contentAsJson(getStreamResult)
      (stream \ 0 \ "summary" \ "case" \ "Creation").asOpt[Int] must beSome(1)
    }
  }
}
