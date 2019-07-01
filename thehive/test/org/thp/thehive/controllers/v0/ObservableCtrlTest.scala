package org.thp.thehive.controllers.v0

import java.io.File
import java.nio.file.{Path, Files => JFiles}
import java.util.UUID

import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Headers, MultipartFormData}
import play.api.test.{FakeRequest, NoTemporaryFileCreator, PlaySpecification}
import play.api.{Configuration, Environment}

import io.scalaland.chimney.dsl._
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{DatabaseBuilder => _, _}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{AppBuilder, Hasher}
import org.thp.thehive.dto.v0.{OutputAttachment, OutputCase, OutputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, DataSrv, LocalUserSrv}

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

class ObservableCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val observableCtrl: ObservableCtrl = app.instanceOf[ObservableCtrl]
    val caseCtrl: CaseCtrl             = app.instanceOf[CaseCtrl]
    val hashers                        = Hasher(app.instanceOf[Configuration].get[Seq[String]]("attachment.hash"): _*)

    s"[$name] observable controller" should {

      "be able to create an observable with string data" in {
        val request = FakeRequest("POST", s"/api/case/#1/artifact")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":"multi\nline\ntest"
              }
            """.stripMargin))
        val result = observableCtrl.create("#1")(request)

        status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

        createdObservables must have size 3
        createdObservables.map(_.dataType) must contain(be_==("autonomous-system")).forall
        createdObservables.flatMap(_.data) must contain(exactly("multi", "line", "test"))
        createdObservables.map(_.sighted) must contain(beFalse).forall
        createdObservables.map(_.message) must contain(beSome("love exciting and new")).forall
        createdObservables.map(_.tags) must contain(be_==(Set("tagfile"))).forall
      }

      "be able to create and search 2 observables with data array" in {
        val request = FakeRequest("POST", s"/api/case/#4/artifact")
          .withHeaders("user" -> "user3")
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
        val result = observableCtrl.create("#4")(request)

        status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

        val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

        createdObservables must have size 3
        createdObservables.map(_.dataType) must contain(be_==("autonomous-system")).forall
        createdObservables.flatMap(_.data) must contain(exactly("observable", "in", "array"))
        createdObservables.map(_.sighted) must contain(beFalse).forall
        createdObservables.map(_.message) must contain(beSome("love exciting and new")).forall
        createdObservables.map(_.tags) must contain(be_==(Set("lol", "tagfile"))).forall

        val requestCase   = FakeRequest("GET", s"/api/v0/case/#4").withHeaders("user" -> "user2")
        val resultCaseGet = caseCtrl.get("#4")(requestCase)

        status(resultCaseGet) shouldEqual 200

        val resultCase = contentAsJson(resultCaseGet).as[OutputCase]
        val requestSearch = FakeRequest("POST", s"/api/case/artifact/_search?range=all&sort=-startDate&nstats=true")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
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
        val resultSearch = observableCtrl.search(requestSearch)

        status(resultSearch) shouldEqual 200

        val resSearchObservables = contentAsJson(resultSearch).as[Seq[OutputObservable]]

        resSearchObservables must have size 3
        resSearchObservables.flatMap(_.data) must contain(exactly("observable", "in", "array"))
      }

      "be able to create and get 2 observables with string data and attachment" in {
        FakeTemporaryFile { tempFile =>
          val hashes    = hashers.fromPath(tempFile.path).map(_.toString)
          val files     = Seq(FilePart("attachment", "myfile.txt", Some("text/plain"), tempFile))
          val dataParts = Map("_json" -> Seq("""
              {
                "dataType":"ip",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"localhost",
                "tags":["local", "host"],
                "data":"127.0.0.1\n127.0.0.2"
              }
            """))
          val request = FakeRequest(
            "POST",
            s"/api/case/#1/artifact",
            Headers("user" -> "user1"),
            body = AnyContentAsMultipartFormData(MultipartFormData(dataParts, files, Nil))
          )
          val result = observableCtrl.create("#1")(request)
          status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
          val createdObservables = contentAsJson(result).as[Seq[OutputObservable]]

          createdObservables must have size 3
          createdObservables.map(_.dataType) must contain(be_==("ip")).forall
          createdObservables.flatMap(_.data) must contain(exactly("127.0.0.1", "127.0.0.2"))
          createdObservables.map(_.sighted) must contain(beFalse).forall
          createdObservables.map(_.message) must contain(beSome("localhost")).forall
          createdObservables.map(_.tags) must contain(be_==(Set("local", "host"))).forall
          val attachmentOption = createdObservables.flatMap(_.attachment).headOption
          attachmentOption must beSome
          val attachment = attachmentOption.get
          attachment.name must beEqualTo("myfile.txt")
          attachment.hashes must containTheSameElementsAs(hashes)
          attachment.size must beEqualTo(tempFile.length())
          attachment.contentType must beEqualTo("text/plain")

          createdObservables.foreach(obs => obs must equalTo(getObservable(obs._id, observableCtrl)))
          ok
        }

      }

      "be able to retrieve an observable" in {
        val maybeObservable = app.instanceOf[Database].transaction { implicit graph =>
          app
            .instanceOf[AlertSrv]
            .initSteps
            .getBySourceId("testType", "testSource", "ref1")
            .observables
            .headOption()
        }
        maybeObservable must beSome
        val observableId = maybeObservable.get._id
        val getRequest = FakeRequest("GET", s"/api/case/artifact/$observableId")
          .withHeaders("user" -> "user3")
        val getResult = observableCtrl.get(observableId)(getRequest)

        status(getResult) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(getResult)}")
        TestObservable(contentAsJson(getResult).as[OutputObservable]) must equalTo(
          TestObservable(
            dataType = "domain",
            data = Some("h.fr"),
            tlp = 3,
            tags = Set("testDomain"),
            ioc = true,
            message = Some("Some weird domain")
          )
        )
      }

      "be able to update and bulk update observables" in {
        val resObservable = createDummyObservable(observableCtrl)
        val requestUp = FakeRequest("PATCH", s"/api/case/artifact/_bulk")
          .withHeaders("user" -> "user1")
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
        val resultUp = observableCtrl.bulkUpdate(requestUp)

        status(resultUp) shouldEqual 204

        val resObsUpdated = resObservable.map(obs => getObservable(obs._id, observableCtrl))

        resObsUpdated.map(_.tags) must contain(be_==(Set("tagfileUp"))).forall
        resObsUpdated.map(_.message) must contain(beSome("love exciting and new edited")).forall
        resObsUpdated.map(_.ioc) must contain(beTrue).forall
        resObsUpdated.map(_.sighted) must contain(beTrue).forall
      }

      "create 2 observables with the same data" in {
        val request1 = FakeRequest("POST", s"/api/case/#1/artifact")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"hostname",
                "message":"here",
                "data":"localhost"
              }
            """))
        val result1 = observableCtrl.create("#1")(request1)
        status(result1) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result1)}")

        getData("localhost", app) must have size 1

        val request2 = FakeRequest("POST", s"/api/case/#3/artifact")
          .withHeaders("user" -> "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"domain",
                "message":"good place",
                "data":"localhost"
              }
            """))
        val result2 = observableCtrl.create("#3")(request2)
        status(result2) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result2)}")

        getData("localhost", app) must have size 1
      }

      "delete an observable" in {
        val resObservable = createDummyObservable(observableCtrl)
        val observableId  = resObservable.head._id
        val requestDelete = FakeRequest("DELETE", s"/api/case/artifact/$observableId")
          .withHeaders("user" -> "user1")
        val resultDelete = observableCtrl.delete(observableId)(requestDelete)

        status(resultDelete) shouldEqual 204
      }
    }
  }

  def getObservable(id: String, observableCtrl: ObservableCtrl): OutputObservable = {
    val requestGet = FakeRequest("GET", s"/api/case/artifact/$id")
      .withHeaders("user" -> "user1")
    val resultGet = observableCtrl.get(id)(requestGet)

    status(resultGet) shouldEqual 200
    contentAsJson(resultGet).as[OutputObservable]
  }

  def createDummyObservable(observableCtrl: ObservableCtrl): Seq[OutputObservable] = {
    val request = FakeRequest("POST", s"/api/case/#1/artifact")
      .withHeaders("user" -> "user1")
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
    val result = observableCtrl.create("#1")(request)

    status(result) shouldEqual 201
    contentAsJson(result).as[Seq[OutputObservable]]
  }

  def getData(data: String, app: AppBuilder): List[Data with Entity] = {
    val dataSrv: DataSrv = app.instanceOf[DataSrv]
    val db: Database     = app.instanceOf[Database]
    db.transaction { implicit graph =>
      dataSrv.initSteps.getByData(data).toList()
    }
  }
}

object FakeTemporaryFile {

  def apply[A](body: Files.TemporaryFile => A): A = {
    val tempFile = JFiles.createTempFile("thehive-", "-test")
    JFiles.write(tempFile, s"hello ${UUID.randomUUID()}".getBytes)
    val fakeTempFile = new Files.TemporaryFile {
      override def path: Path                                 = tempFile
      override def file: File                                 = tempFile.toFile
      override def temporaryFileCreator: TemporaryFileCreator = NoTemporaryFileCreator
    }
    try body(fakeTempFile)
    finally JFiles.deleteIfExists(tempFile)
  }
}
