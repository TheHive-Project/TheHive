package org.thp.thehive.controllers.v0

import akka.actor.{ActorIdentity, Identify, PoisonPill}
import akka.util.Timeout
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.services.EventSrv
import org.thp.thehive.models._
import org.thp.thehive.services.StreamTopic
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class StreamCtrlTest extends PlaySpecification with TestAppBuilder {

  def killStreamActor(eventSrv: EventSrv, streamId: String)(implicit ec: ExecutionContext): Unit =
    eventSrv
      .publishAsk(StreamTopic(streamId))(Identify(1))(Timeout(2.seconds))
      .foreach {
        case ActorIdentity(1, Some(streamActor)) => streamActor ! PoisonPill
        case _                                   =>
      }

  "stream controller" should {
    "create a stream" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = streamCtrl.create(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      val streamId = contentAsString(result)
      killStreamActor(app.eventSrv, streamId)(app.executionContext)
      streamId must not(beEmpty)
    }

    "get a case related stream" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      implicit val authContext: AuthContext = DummyUserSrv(permissions = Permissions.all, organisation = "cert").authContext

      val createStreamRequest = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "certuser@thehive.local")
      val createStreamResult = streamCtrl.create(createStreamRequest)

      status(createStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(createStreamResult)}")
      val streamId = contentAsString(createStreamResult)

      // Add an event
      database.tryTransaction { implicit graph =>
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
          customFields = Nil,
          caseTemplate = None,
          additionalTasks = Nil,
          sharingParameters = Map.empty,
          taskRule = None,
          observableRule = None
        )
      } must beASuccessfulTry

      val getStreamRequest = FakeRequest("GET", s"/api/stream/$streamId")
        .withHeaders("user" -> "certuser@thehive.local")
      val getStreamResult = streamCtrl.get(streamId)(getStreamRequest)

      status(getStreamResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(getStreamResult)}")
      val stream = contentAsJson(getStreamResult)
      killStreamActor(app.eventSrv, streamId)(app.executionContext)
      (stream \ 0 \ "summary" \ "case" \ "Creation").asOpt[Int] must beSome(1)
    }
  }
}
