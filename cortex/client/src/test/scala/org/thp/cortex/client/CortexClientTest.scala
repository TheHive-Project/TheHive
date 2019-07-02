package org.thp.cortex.client

import java.util.Date

import akka.actor.ActorSystem
import org.specs2.mock.Mockito
import org.thp.cortex.dto.v0.{CortexOutputJob, InputCortexArtifact, JobStatus, OutputCortexAnalyzer}
import org.thp.scalligraph.AppBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

class CortexClientTest extends PlaySpecification with Mockito with FakeCortexClient {
  s"CortexClient" should {
    lazy val app = AppBuilder()

    implicit lazy val ws: CustomWSAPI      = app.instanceOf[CustomWSAPI]
    implicit lazy val auth: Authentication = KeyAuthentication("test")
    implicit lazy val system: ActorSystem  = app.instanceOf[ActorSystem]

    "handle requests properly" in {
      withCortexClient { client =>
        val analyzers: Seq[OutputCortexAnalyzer] = await(client.listAnalyser)

        analyzers.length shouldEqual 2
        analyzers.head.cortexIds must beSome(List(client.name))
        analyzers.head.name shouldEqual "anaTest1"

        val oneAnalyzer: OutputCortexAnalyzer = await(client.getAnalyzer("anaTest2"))

        oneAnalyzer.id shouldEqual "anaTest2"
        oneAnalyzer.name shouldEqual "anaTest2"

        val searchedAnalyzer: OutputCortexAnalyzer = await(client.getAnalyzerByName("anaTest1"))

        searchedAnalyzer.copy(cortexIds = None) should equalTo(
          OutputCortexAnalyzer(
            id = "anaTest1",
            name = "anaTest1",
            version = "1",
            description = "Ego vero sic intellego, Patres conscripti, nos hoc tempore in provinciis decernendis perpetuae pacis",
            dataTypeList = Seq("test")
          )
        )

        val outputJob: CortexOutputJob = await(client.analyse(searchedAnalyzer.id, InputCortexArtifact(1, 1, "test", "test", Some("test"), None)))

        outputJob should equalTo(
          CortexOutputJob(
            id = "AWuYKFatq3Rtqym9DFmL",
            workerId = "anaTest1",
            workerName = "anaTest1",
            workerDefinition = "anaTest1",
            date = new Date(1561625908856L),
            startDate = None,
            endDate = None,
            status = JobStatus.Waiting,
            data = Some("https://www.faux-texte.com/lorem-ipsum-2.htm"),
            attachment = None,
            organization = "test",
            dataType = "domain",
            attributes = Json.obj("tlp" -> 2, "message" -> "0ad6e75a-1a2e-419a-b54a-7a92d6528404", "parameters" -> JsObject.empty, "pap" -> 2),
            None
          )
        )

      }
    }
  }
}
