package org.thp.thehive.controllers.v0

import java.util.Date

import akka.stream.Materializer
import gremlin.scala.Graph
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, OrganisationSrv}
import play.api.libs.json.JsObject
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class StreamCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val streamCtrl: StreamCtrl   = app.instanceOf[StreamCtrl]
    val caseSrv: CaseSrv         = app.instanceOf[CaseSrv]
    val db: Database             = app.instanceOf[Database]
    val orgaSrv: OrganisationSrv = app.instanceOf[OrganisationSrv]

    def createCase(nb: Int)(implicit graph: Graph) =
      caseSrv.create(
        Case(nb, s"case audit $nb", s"desc audit $nb", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
        None,
        orgaSrv.getOrFail("cert").get,
        Set.empty,
        Map.empty,
        None,
        Nil
      )(graph, dummyUserSrv.authContext)

    def createStream = {
      val request = FakeRequest("POST", "/api/stream")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val result = streamCtrl.create(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsString(result)
    }

    def checkStream(streamId: String, entity: String) = {
      val request = FakeRequest("GET", s"/api/stream/$streamId")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val result = streamCtrl.get(streamId)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val stream = contentAsJson(result).as[List[JsObject]]

      stream must not(beEmpty)

      val entityEvent = stream.find(o => (o \ "summary" \ entity).isDefined).get

      (entityEvent \ "summary" \ entity \ "Creation").as[Int] shouldEqual 1
    }

    s"$name stream controller" should {
      "create a stream" in {
        createStream must not(beEmpty)
      }

      "get a case related stream" in {
        val streamId = createStream
        // Add an event
        db.tryTransaction(implicit graph => createCase(0))

        checkStream(streamId, "case")
      }
    }
  }
}
