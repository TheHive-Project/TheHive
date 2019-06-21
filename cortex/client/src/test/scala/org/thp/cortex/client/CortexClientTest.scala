package org.thp.cortex.client

import akka.actor.ActorSystem
import org.specs2.mock.Mockito
import org.thp.cortex.dto.client.OutputCortexAnalyzer
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, ControllerComponents, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.{PlaySpecification, WithServer}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CortexClientTest extends PlaySpecification with Mockito {

  s"CortexClient" should {
    lazy val app = GuiceApplicationBuilder()
      .configure("play.allowGlobalApplication" → true)
      .router(Router.from {
        case play.api.routing.sird.GET(p"/api/analyzer") ⇒
          Action {
            Results.Ok.sendResource("analyzers.json")(GuiceApplicationBuilder().injector.instanceOf[ControllerComponents].fileMimeTypes)
          }
      })
      .build()

    implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    implicit lazy val ws: CustomWSAPI      = app.injector.instanceOf[CustomWSAPI]
    implicit lazy val auth: Authentication = PasswordAuthentication("test", "test")
    implicit lazy val system: ActorSystem  = app.actorSystem
    val mockLogger                         = mock[Logger]

    lazy val client = new CortexClient("test", s"http://127.0.0.1:3333", 3.seconds, 3) {
      override lazy val logger: Logger = mockLogger
    }

    "handle get analyzers properly" in new WithServer(app, port = 3333) {
      val analyzers: Seq[OutputCortexAnalyzer] = await(client.listAnalyser)

      analyzers.length shouldEqual 2
      analyzers.head.cortexIds must beNone
      analyzers.head.name shouldEqual "anaTest1"
    }
  }

}
