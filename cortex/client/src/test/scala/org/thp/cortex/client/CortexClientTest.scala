package org.thp.cortex.client

import akka.actor.ActorSystem
import org.specs2.mock.Mockito
import org.thp.cortex.dto.v0.{CortexOutputJob, InputCortexArtifact, OutputCortexAnalyzer}
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.{PlaySpecification, WithServer}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.Source

class CortexClientTest extends PlaySpecification with Mockito {

  def getAnalyzers: Seq[OutputCortexAnalyzer] = {
    val dataSource = Source.fromFile(getClass.getResource("/analyzers.json").getPath)
    val data       = dataSource.mkString
    dataSource.close()

    Json.parse(data).as[Seq[OutputCortexAnalyzer]]
  }

  s"CortexClient" should {
    lazy val app = GuiceApplicationBuilder()
      .configure("play.allowGlobalApplication" → true, "play.filters.enabled" -> Nil)
      .router(Router.from {
        case play.api.routing.sird.GET(p"/api/analyzer") ⇒
          Action {
            Results.Ok.sendResource("analyzers.json")(GuiceApplicationBuilder().injector.instanceOf[ControllerComponents].fileMimeTypes)
          }
        case play.api.routing.sird.GET(p"/api/analyzer/$id") ⇒
          Action {
            Results.Ok(Json.toJson(getAnalyzers.find(_.id == id).get))
          }
        case play.api.routing.sird.POST(p"/api/analyzer/_search") ⇒
          Action {
            Results.Ok(Json.toJson(getAnalyzers.head))
          }
      })
      .build()

    implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    implicit lazy val ws: CustomWSAPI      = app.injector.instanceOf[CustomWSAPI]
    implicit lazy val auth: Authentication = KeyAuthentication("test")
    implicit lazy val system: ActorSystem  = app.actorSystem
    val mockLogger                         = mock[Logger]

    val clientName = "test"
    val clientPort = 3333
    lazy val client = new CortexClient(clientName, s"http://127.0.0.1:$clientPort", 3.seconds, 3) {
      override lazy val logger: Logger = mockLogger
    }

    "handle requests properly" in new WithServer(app, port = clientPort) {
      val analyzers: Seq[OutputCortexAnalyzer] = await(client.listAnalyser)

      analyzers.length shouldEqual 2
      analyzers.head.cortexIds must beSome(List(clientName))
      analyzers.head.name shouldEqual "anaTest1"

      val oneAnalyzer: OutputCortexAnalyzer = await(client.getAnalyzer("anaTest2"))

      oneAnalyzer.id shouldEqual "anaTest2"
      oneAnalyzer.name shouldEqual "anaTest2"

//      val outputJob: CortexOutputJob = await(client.analyse("Abuse_Finder_2_0", InputCortexArtifact(1, 1, "test", "test", Some("test"), None)))

      val searchedAnalyzer: OutputCortexAnalyzer = await(client.getAnalyzerByName("anaTest1"))

      searchedAnalyzer.copy(cortexIds = None) shouldEqual getAnalyzers.find(_.name == "anaTest1").get
    }
  }

}
