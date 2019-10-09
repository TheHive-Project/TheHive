package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{InputShare, ObservablesFilter, OutputShare, TasksFilter}
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services.CaseSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class ShareCtrlTest extends PlaySpecification with Mockito {
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
    val shareCtrl: ShareCtrl = app.instanceOf[ShareCtrl]
    val caseSrv: CaseSrv     = app.instanceOf[CaseSrv]
    val db: Database         = app.instanceOf[Database]

    "manage shares for a case" in {
      val inputShare = Json.obj("shares" -> List(Json.toJson(InputShare("cert", "all", TasksFilter.all, ObservablesFilter.all))))
      def getShares = {
        val requestGet = FakeRequest("GET", "/api/case/#4/shares")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        val resGet = shareCtrl.listShareCases("#4")(requestGet)

        status(resGet) shouldEqual 200

        val l = contentAsJson(resGet).as[List[OutputShare]]

        l.length shouldEqual 2

        l
      }
      val request = FakeRequest("PUT", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result = shareCtrl.shareCase("#4")(request)

      status(result) shouldEqual 201

      val requestAgain = FakeRequest("PUT", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result2 = shareCtrl.shareCase("#4")(requestAgain)

      status(result2) shouldEqual 201

      val l     = getShares
      val share = l.find(_.organisationName == "cert")

      share must beSome.which(s => {
        s.profileName shouldEqual "all"
        s.organisationName shouldEqual "cert"
      })

      val requestUpdate = FakeRequest("PUT", "/api/case/#4/shares")
        .withJsonBody(Json.obj("shares" -> List(Json.toJson(InputShare("cert", "read-only", TasksFilter.all, ObservablesFilter.all)))))
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result3 = shareCtrl.shareCase("#4")(requestUpdate)

      status(result3) shouldEqual 201

      val l2     = getShares
      val share2 = l2.find(_.organisationName == "cert")

      share2 must beSome.which(s => {
        s.profileName shouldEqual "read-only"
        s.organisationName shouldEqual "cert"
      })
    }

    "fetch shares for a task" in db.roTransaction { implicit graph =>
      val tasks = caseSrv.get("#4").tasks(dummyUserSrv.authContext).toList

      tasks must not(beEmpty)

      val task4 = tasks.find(_.title == "case 4 task 1")

      task4 must beSome

      val request = FakeRequest("GET", s"/api/case/#4/task/${task4.get._id}/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result = shareCtrl.listShareTasks("#4", task4.get._id)(request)

      status(result) shouldEqual 200
      contentAsJson(result).as[List[OutputShare]] must not(beEmpty)
    }


  }

}
