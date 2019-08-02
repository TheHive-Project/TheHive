package org.thp.cortex.client

import java.util.Date

import akka.actor.Terminated
import org.specs2.mock.Mockito
import org.specs2.specification.core.Fragments
import org.thp.cortex.dto.v0._
import org.thp.scalligraph.AppBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.test.PlaySpecification

import scala.concurrent.Future
import scala.concurrent.duration._

class CortexClientTest extends PlaySpecification with Mockito {
  lazy val app = AppBuilder()
  app.bindToProvider[CortexClient, TestCortexClientProvider]
  val client: CortexClient = app.instanceOf[CortexClient]

  override def map(fragments: => Fragments): Fragments =
    fragments ^ step(afterAll())

  def afterAll(): Future[Terminated] = app.app.actorSystem.terminate()

  s"CortexClient" should {
    "handle requests properly" in {
      val analyzers: Seq[OutputCortexWorker] = await(client.listAnalyser)

      analyzers.length shouldEqual 2
//      analyzers.head.cortexIds must contain(exactly(client.name))
      analyzers.head.name shouldEqual "anaTest1"

      val oneAnalyzer: OutputCortexWorker = await(client.getAnalyzer("anaTest2"))

      oneAnalyzer.id shouldEqual "anaTest2"
      oneAnalyzer.name shouldEqual "anaTest2"

      val searchedAnalyzer: OutputCortexWorker = await(client.getAnalyzerByName("anaTest1"))

      searchedAnalyzer should equalTo(
        OutputCortexWorker(
          id = "anaTest1",
          name = "anaTest1",
          version = "1",
          description = "Ego vero sic intellego, Patres conscripti, nos hoc tempore in provinciis decernendis perpetuae pacis",
          dataTypeList = Seq("test"),
          maxTlp = 2,
          maxPap = 2
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
          status = CortexJobStatus.Waiting,
          data = Some("https://www.faux-texte.com/lorem-ipsum-2.htm"),
          attachment = None,
          organization = "test",
          dataType = "domain",
          attributes = Json.obj("tlp" -> 2, "message" -> "0ad6e75a-1a2e-419a-b54a-7a92d6528404", "parameters" -> JsObject.empty, "pap" -> 2),
          None,
          CortexJobType.analyzer
        )
      )

      val successfulJob = await(client.getReport("XQuYKFert7Rtcvm9DFmT", 0.second))

      successfulJob.report must beSome(
        CortexOutputReport(
          summary = Json.parse("""{
                                       "taxonomies": [
                                         {
                                           "level": "info",
                                           "namespace": "test",
                                           "predicate": "data",
                                           "value": "test"
                                         }
                                       ]
                                     }""").as[JsObject],
          success = true,
          full = Json.parse("""{
                                    "data": "imageedit_2_3904987689.jpg",
                                    "input": {
                                      "file": "attachment7619802021796183482",
                                      "filename": "imageedit_2_3904987689.jpg",
                                      "dataType": "file",
                                      "tlp": 2,
                                      "message": "179e85c4-4170-45fe-9d2d-3173539554a6",
                                      "contentType": "image/jpeg",
                                      "parameters": {
                                      },
                                      "config": {
                                        "proxy_https": null,
                                        "cacerts": null,
                                        "max_pap": 2,
                                        "jobTimeout": 30,
                                        "check_tlp": true,
                                        "proxy_http": null,
                                        "max_tlp": 2,
                                        "auto_extract_artifacts": false,
                                        "jobCache": 10,
                                        "check_pap": true
                                      },
                                      "pap": 2
                                    }
                                  }""").as[JsObject],
          artifacts = Json.parse("""[
                                         {
                                           "attachment": {
                                             "contentType": "application/octet-stream",
                                             "id": "e64871cf4652cb6e1babc06a376e7c79256dd6b967ca845ae06708cbeb686663",
                                             "name": "passwd",
                                             "size": 2644
                                           },
                                           "dataType": "file",
                                           "message": null,
                                           "tags": ["file", "virus"],
                                           "tlp": 3
                                         },
                                         {
                                           "data": "127.0.0.1",
                                           "dataType": "ip",
                                           "message": null,
                                           "tags": [
                                             "localhost"
                                           ],
                                           "tlp": 2
                                         }
                                       ]""").as[List[CortexOutputArtifact]],
          operations = JsArray.empty.as[List[CortexOutputOperation]],
          errorMessage = None,
          input = None
        )
      )

      val searchedResponder: OutputCortexWorker = await(client.getResponderByName("respTest1"))

      searchedResponder should equalTo(
        OutputCortexWorker(
          id = "respTest1",
          name = "respTest1",
          version = "1",
          description = "Ego vero sic intellego, Patres conscripti, nos hoc tempore in provinciis decernendis perpetuae pacis",
          dataTypeList = Seq("test", "case_task"),
          maxPap = 3,
          maxTlp = 2
//          cortexIds = List("test")
        )
      )

      val responder = await(client.getResponder("respTest2"))

      responder should equalTo(
        OutputCortexWorker(
          id = "respTest2",
          name = "respTest2",
          version = "2",
          description = "nos hoc tempore in provinciis decernendis perpetuae pacis",
          dataTypeList = Seq("test", "dummy"),
          maxPap = 2, // FIXME
          maxTlp = 3  // FIXME
//          cortexIds = List("test", "test2")
        )
      )
    }
  }
//  }
}
