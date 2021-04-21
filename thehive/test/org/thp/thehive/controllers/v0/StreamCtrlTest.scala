package org.thp.thehive.controllers.v0

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.models._
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date

class StreamCtrlTest extends PlaySpecification with TestAppBuilder {
  "stream controller" should {
    "create a stream" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = streamCtrl.create(request)

      status(result)          must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsString(result) must not(beEmpty)
    }

    "get a case related stream" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      implicit val authContext: AuthContext = DummyUserSrv(permissions = Permissions.all).authContext

      val createStreamRequest = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val createStreamResult = streamCtrl.create(createStreamRequest)

      status(createStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(createStreamResult)}")
      val streamId = contentAsString(createStreamResult)

      // Add an event
      database.tryTransaction { implicit graph =>
        val organisation = organisationSrv.getOrFail(EntityName("cert")).get
        caseSrv.create(
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
      val getStreamResult = streamCtrl.get(streamId)(getStreamRequest)

      status(getStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(getStreamResult)}")
      val stream = contentAsJson(getStreamResult)
      (stream \ 0 \ "summary" \ "case" \ "Creation").asOpt[Int] must beSome(1)
    }
  }
}
