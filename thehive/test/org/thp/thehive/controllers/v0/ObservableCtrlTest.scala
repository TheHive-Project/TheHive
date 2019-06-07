package org.thp.thehive.controllers.v0

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Path
import java.util.UUID

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v0.{OutputCase, OutputObservable}
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.Files
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Headers, MultipartFormData}
import play.api.test.{FakeRequest, NoTemporaryFileCreator, PlaySpecification}
import play.api.{Configuration, Environment}

class ObservableCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv          = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
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
    val observableCtrl: ObservableCtrl  = app.instanceOf[ObservableCtrl]
    implicit lazy val mat: Materializer = app.instanceOf[Materializer]

    s"[$name] observable controller" should {

      "be able to create an observable with string data" in {
        val request = FakeRequest("POST", s"/api/case/#1/artifact")
          .withHeaders("user" → "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":"test\ntest2"
              }
            """.stripMargin))
        val result = observableCtrl.create("#1")(request)

        status(result) shouldEqual 201

        val resObservable = contentAsJson(result).as[Seq[OutputObservable]].head

        resObservable.data must beSome[String]
        resObservable.data.get shouldEqual "test\ntest2"
        resObservable.sighted must beFalse
        resObservable.ioc must beFalse
        resObservable.dataType shouldEqual "autonomous-system"

        val requestGet = FakeRequest("GET", s"/api/case/artifact/${resObservable._id}")
          .withHeaders("user" → "user1")
        val resultGet = observableCtrl.get(resObservable._id)(requestGet)

        status(resultGet) shouldEqual 200
        contentAsJson(resultGet).as[OutputObservable].tags shouldEqual Seq("tagfile")
      }

      "be able to create and search 2 observables with data array" in {
        val request = FakeRequest("POST", s"/api/case/#4/artifact")
          .withHeaders("user" → "user3")
          .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile", "lol"],
                "data":["test1", "test2"]
              }
            """.stripMargin))
        val result = observableCtrl.create("#4")(request)

        status(result) shouldEqual 201

        val resObservables = contentAsJson(result).as[Seq[OutputObservable]]

        resObservables.size shouldEqual 2
        resObservables.head.data.get shouldEqual "test1"
        resObservables.last.data.get shouldEqual "test2"

        val requestCase   = FakeRequest("GET", s"/api/v0/case/#4").withHeaders("user" → "user2")
        val resultCaseGet = app.instanceOf[CaseCtrl].get("#4")(requestCase)

        status(resultCaseGet) shouldEqual 200

        val resultCase = contentAsJson(resultCaseGet).as[OutputCase]
        val requestSearch = FakeRequest("POST", s"/api/case/artifact/_search?range=all&sort=-startDate&nstats=true")
          .withHeaders("user" → "user2")
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

        resSearchObservables.size shouldEqual 2
      }

      "be able to create and get 2 observables with string data and attachment" in {
        val tempFile  = FakeTemporaryFile()
        val files     = Seq(FilePart("attachment", "myfile.txt", Some("text/plain"), tempFile))
        val dataParts = Map("_json" → Seq("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":"test\ntest2"
              }
            """.stripMargin))
        val request = FakeRequest(
          "POST",
          s"/api/case/#1/artifact",
          Headers("user" → "user1"),
          body = AnyContentAsMultipartFormData(MultipartFormData(dataParts, files, Nil))
        )
        val result = observableCtrl.create("#1")(request)

        status(result) shouldEqual 201
        FakeTemporaryFile.unapply(tempFile) must beSome(tempFile.path.toString)

        val resObservables = contentAsJson(result).as[Seq[OutputObservable]]

        resObservables.size shouldEqual 2
        resObservables.head.data.get shouldEqual "test\ntest2"
        resObservables.last.data must beNone

        val requestGet = FakeRequest("GET", s"/api/case/artifact/${resObservables.head._id}")
          .withHeaders("user" → "user1")
        val resultGet = observableCtrl.get(resObservables.head._id)(requestGet)

        status(resultGet) shouldEqual 200
        contentAsJson(resultGet).as[OutputObservable].tags shouldEqual Seq("tagfile")
      }

      "be able to update and bulk update observables" in {
        val request = FakeRequest("POST", s"/api/case/#1/artifact")
          .withHeaders("user" → "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"autonomous-system",
                "ioc":false,
                "sighted":false,
                "tlp":2,
                "message":"love exciting and new",
                "tags":["tagfile"],
                "data":"test\ntest2"
              }
            """.stripMargin))
        val result = observableCtrl.create("#1")(request)

        status(result) shouldEqual 201

        val resObservable = contentAsJson(result).as[Seq[OutputObservable]].head
        val requestUp = FakeRequest("PATCH", s"/api/case/artifact/${resObservable._id}")
          .withHeaders("user" → "user1")
          .withJsonBody(Json.parse("""
              {
                "dataType":"domain",
                "ioc":true,
                "sighted":true,
                "tlp":1,
                "message":"love exciting and new edited",
                "tags":["tagfileUp"]
              }
            """.stripMargin))
        val resultUp = observableCtrl.update(resObservable._id)(requestUp)

        status(resultUp) shouldEqual 204
      }
    }
  }
}

case class FakeTemporaryFile(name: String) extends Files.TemporaryFile {
  def path: Path = file.toPath

  def file = new File(name)

  def temporaryFileCreator: TemporaryFileCreator = NoTemporaryFileCreator
}

object FakeTemporaryFile {

  def apply(): Files.TemporaryFile = {
    val file = new File("/tmp/test.txt")
    val bw   = new BufferedWriter(new FileWriter(file))
    bw.write(s"hello ${UUID.randomUUID()}")
    bw.close()
    FakeTemporaryFile(file.getAbsolutePath)
  }

  def unapply(arg: Files.TemporaryFile): Option[String] = {
    val f = new File(arg.path.toString)
    if (f.exists()) f.delete()

    Some(arg.path.toString)
  }
}
