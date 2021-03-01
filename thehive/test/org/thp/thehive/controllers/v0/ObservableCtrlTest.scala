package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.utils.Hasher
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{OutputAttachment, OutputCase, OutputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.DataOps._
import org.thp.thehive.services.DataSrv
import play.api.Configuration
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Headers, MultipartFormData}
import play.api.test.{FakeRequest, NoTemporaryFileCreator, PlaySpecification}

import java.io.File
import java.nio.file.{Path, Files => JFiles}
import java.util.UUID

case class TestObservable(
    dataType: String,
    data: Option[String] = None,
    attachment: Option[OutputAttachment] = None,
    tlp: Int = 2,
    tags: Set[String] = Set.empty,
    ioc: Boolean = false,
    sighted: Boolean = false,
    message: Option[String] = None
)

object TestObservable {

  def apply(outputObservable: OutputObservable): TestObservable =
    outputObservable.into[TestObservable].transform
}

class ObservableCtrlTest extends PlaySpecification with TestAppBuilder {
  "observable controller" should {

    "be able to create an observable with string data" in testApp { app =>
      val request = FakeRequest("POST", s"/api/case/1/artifact")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":["multi","line","test"]
              }
            """.stripMargin))
      val result = app[ObservableCtrl].createInCase("1")(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

      createdObservables must have size 3
      createdObservables.map(_.dataType) must contain(be_==("autonomous-system")).forall
      createdObservables.flatMap(_.data) must contain(exactly("multi", "line", "test"))
      createdObservables.map(_.sighted) must contain(beFalse).forall
      createdObservables.map(_.message) must contain(beSome("love exciting and new")).forall
      createdObservables.map(_.tags) must contain(be_==(Set("tagfile"))).forall
    }

    "be able to create and search 2 observables with data array" in testApp { app =>
      val request = FakeRequest("POST", s"/api/case/1/artifact")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile", "lol"],
                "data":["observable", "in", "array"]
              }
            """.stripMargin))
      val result = app[ObservableCtrl].createInCase("1")(request)

      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

      createdObservables must have size 3
      createdObservables.map(_.dataType) must contain(be_==("autonomous-system")).forall
      createdObservables.flatMap(_.data) must contain(exactly("observable", "in", "array"))
      createdObservables.map(_.sighted) must contain(beFalse).forall
      createdObservables.map(_.message) must contain(beSome("love exciting and new")).forall
      createdObservables.map(_.tags) must contain(be_==(Set("lol", "tagfile"))).forall

      val requestCase =
        FakeRequest("GET", s"/api/v0/case/1").withHeaders("user" -> "certuser@thehive.local")
      val resultCaseGet = app[CaseCtrl].get("1")(requestCase)

      status(resultCaseGet) shouldEqual 200

      val resultCase = contentAsJson(resultCaseGet).as[OutputCase]
      val requestSearch = FakeRequest("POST", s"/api/case/artifact/_search?range=all&sort=-startDate&nstats=true")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse(s"""
              {
                "query":{
                   "_and":[
                      {
                         "_and":[
                            {
                               "_parent":{
                                  "_type":"case",
                                  "_query":{
                                     "_id":"${resultCase._id}"
                                  }
                               }
                            },
                            {
                               "status":"Ok"
                            }
                         ]
                      }
                   ]
                }
             }
            """.stripMargin))
      val resultSearch = app[ObservableCtrl].search(requestSearch)

      status(resultSearch) should equalTo(200).updateMessage(s => s"$s\n${contentAsString(resultSearch)}")

      val resSearchObservables = contentAsJson(resultSearch)(defaultAwaitTimeout, app[Materializer]).as[Seq[OutputObservable]]

      resSearchObservables must have size 4
      resSearchObservables.flatMap(_.data) must contain(exactly("observable", "in", "array", "h.fr"))
    }

    "be able to create and get 2 observables with string data" in testApp { app =>
      WithFakeTemporaryFile { tempFile =>
        val files     = Seq(FilePart("attachment", "myfile.txt", Some("text/plain"), tempFile))
        val dataParts = Map("_json" -> Seq("""
              {
                "dataType":"ip",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"localhost",
                "tags":["local", "host"],
                "data":["127.0.0.1","127.0.0.2"]
              }
            """))
        val request = FakeRequest(
          "POST",
          s"/api/case/#1/artifact",
          Headers("user" -> "certuser@thehive.local"),
          body = AnyContentAsMultipartFormData(MultipartFormData(dataParts, files, Nil))
        )
        val result = app[ObservableCtrl].createInCase("1")(request)
        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

        createdObservables must have size 2
        createdObservables.map(_.dataType) must contain(be_==("ip")).forall
        createdObservables.flatMap(_.data) must contain(exactly("127.0.0.1", "127.0.0.2"))
        createdObservables.map(_.sighted) must contain(beFalse).forall
        createdObservables.map(_.message) must contain(beSome("localhost")).forall
        createdObservables.map(_.tags) must contain(be_==(Set("local", "host"))).forall
        val attachmentOption = createdObservables.flatMap(_.attachment).headOption
        attachmentOption must beNone
      }
    }

    "be able to create and get 2 observables with string data and attachment" in testApp { app =>
      WithFakeTemporaryFile { tempFile =>
        val hasher      = Hasher(app.apply[Configuration].get[Seq[String]]("attachment.hash"): _*)
        val hashes      = hasher.fromPath(tempFile.path).map(_.toString)
        val helloHashes = hasher.fromString("Hello world").map(_.toString)
        val files       = Seq(FilePart("attachment", "myfile.txt", Some("text/plain"), tempFile))
        val dataParts   = Map("_json" -> Seq("""
              {
                "dataType":"file",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"localhost",
                "tags":["local", "host"],
                "data":["hello.txt;text/plain;SGVsbG8gd29ybGQ="]
              }
            """))
        val request = FakeRequest(
          "POST",
          s"/api/alert/testType;testSource;ref2/artifact",
          Headers("user" -> "certuser@thehive.local"),
          body = AnyContentAsMultipartFormData(MultipartFormData(dataParts, files, Nil))
        )
        val result = app[ObservableCtrl].createInAlert("testType;testSource;ref2")(request)
        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

        createdObservables must have size 2
        createdObservables.map(_.dataType) must contain(be_==("file")).forall
        createdObservables.flatMap(_.data) must beEmpty
        createdObservables.map(_.sighted) must contain(beFalse).forall
        createdObservables.map(_.message) must contain(beSome("localhost")).forall
        createdObservables.map(_.tags) must contain(be_==(Set("local", "host"))).forall
        val attachments = createdObservables.flatMap(_.attachment)
        attachments must have size 2
        attachments must contain(beLike[OutputAttachment] {
          case attachment =>
            attachment.name must beEqualTo("myfile.txt")
            attachment.hashes must containTheSameElementsAs(hashes)
            attachment.size must beEqualTo(tempFile.length())
            attachment.contentType must beEqualTo("text/plain")
        })
        attachments must contain(beLike[OutputAttachment] {
          case attachment =>
            attachment.name must beEqualTo("hello.txt")
            attachment.hashes must containTheSameElementsAs(helloHashes)
            attachment.size must beEqualTo(11)
            attachment.contentType must beEqualTo("text/plain")
        })
        createdObservables.foreach(obs => obs must equalTo(getObservable(obs._id, app[ObservableCtrl])))
        ok
      }
    }

    "be able to update and bulk update observables" in testApp { app =>
      val resObservable = createDummyObservable(app[ObservableCtrl])
      val requestUp = FakeRequest("PATCH", s"/api/case/artifact/_bulk")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse(s"""
              {
                "ids":${Json.toJson(resObservable.map(_._id))},
                "ioc":true,
                "sighted":true,
                "tlp":1,
                "message":"love exciting and new edited",
                "tags":["tagfileUp"]
              }
            """))
      val resultUp = app[ObservableCtrl].bulkUpdate(requestUp)

      status(resultUp) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(resultUp)}")

      val resObsUpdated = resObservable.map(obs => getObservable(obs._id, app[ObservableCtrl]))

      resObsUpdated.map(_.tags) must contain(be_==(Set("tagfileUp"))).forall
      resObsUpdated.map(_.message) must contain(beSome("love exciting and new edited")).forall
      resObsUpdated.map(_.ioc) must contain(beTrue).forall
      resObsUpdated.map(_.sighted) must contain(beTrue).forall
    }

    "create 2 observables with the same data" in testApp { app =>
      val request1 = FakeRequest("POST", s"/api/case/1/artifact")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {
                "dataType":"hostname",
                "message":"here",
                "data":"localhost"
              }
            """))
      val result1 = app[ObservableCtrl].createInCase("1")(request1)
      status(result1) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

      getData("localhost", app) must have size 1

      val request2 = FakeRequest("POST", s"/api/case/2/artifact")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse("""
              {
                "dataType":"domain",
                "message":"good place",
                "data":"localhost"
              }
            """))
      val result2 = app[ObservableCtrl].createInCase("2")(request2)
      status(result2) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result2)}")

      getData("localhost", app) must have size 1
    }

    "delete an observable" in testApp { app =>
      val resObservable = createDummyObservable(app[ObservableCtrl])
      val observableId  = resObservable.head._id
      val requestDelete = FakeRequest("DELETE", s"/api/case/artifact/$observableId")
        .withHeaders("user" -> "certuser@thehive.local")
      val resultDelete = app[ObservableCtrl].delete(observableId)(requestDelete)

      status(resultDelete) shouldEqual 204
    }
  }

  def getObservable(id: String, observableCtrl: ObservableCtrl): OutputObservable = {
    val requestGet = FakeRequest("GET", s"/api/case/artifact/$id")
      .withHeaders("user" -> "certuser@thehive.local")
    val resultGet = observableCtrl.get(id)(requestGet)

    status(resultGet) shouldEqual 200
    contentAsJson(resultGet).as[OutputObservable]
  }

  def createDummyObservable(observableCtrl: ObservableCtrl): Seq[OutputObservable] = {
    val request = FakeRequest("POST", s"/api/case/1/artifact")
      .withHeaders("user" -> "certuser@thehive.local")
      .withJsonBody(Json.parse(s"""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":"${UUID.randomUUID()}\\n${UUID.randomUUID()}"
              }
            """))
    val result = observableCtrl.createInCase("1")(request)

    status(result) shouldEqual 201
    contentAsJson(result).as[Seq[OutputObservable]]
  }

  def getData(data: String, app: AppBuilder): List[Data with Entity] = {
    val dataSrv: DataSrv = app.apply[DataSrv]
    val db: Database     = app.apply[Database]
    db.roTransaction { implicit graph =>
      dataSrv.startTraversal.getByData(data).toList
    }
  }
}

object WithFakeTemporaryFile {

  def apply[A](body: Files.TemporaryFile => A): A = {
    val tempFile = JFiles.createTempFile("thehive-", "-test")
    JFiles.write(tempFile, s"hello ${UUID.randomUUID()}".getBytes)
    val fakeTempFile = new Files.TemporaryFile {
      override def path: Path                                 = tempFile
      override def file: File                                 = tempFile.toFile
      override def temporaryFileCreator: TemporaryFileCreator = NoTemporaryFileCreator
    }
    try body(fakeTempFile)
    finally {
      JFiles.deleteIfExists(tempFile)
      ()
    }
  }
}
