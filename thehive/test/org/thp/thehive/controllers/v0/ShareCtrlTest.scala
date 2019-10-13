package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0._
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services.CaseSrv
import play.api.libs.json.{JsArray, Json}
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
    val organisationCtrl     = app.instanceOf[OrganisationCtrl]

    def getShares(caseId: String) = {
      val requestGet = FakeRequest("GET", s"/api/case/$caseId/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val resGet = shareCtrl.listShareCases(caseId)(requestGet)

      status(resGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resGet)}")

      val l = contentAsJson(resGet).as[List[OutputShare]]

      l
    }

    "manage shares for a case" in {
      val inputShare = Json.obj("shares" -> List(Json.toJson(InputShare("cert", "all", TasksFilter.all, ObservablesFilter.all))))

      val request = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result = shareCtrl.shareCase("#4")(request)

      status(result) must equalTo(201)

      val requestAgain = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result2 = shareCtrl.shareCase("#4")(requestAgain)

      status(result2) must equalTo(201)

      val l     = getShares("#4")
      val share = l.find(_.organisationName == "cert")

      share must beSome.which(s => {
        s.profileName shouldEqual "all"
        s.organisationName shouldEqual "cert"
      })

      val requestUpdate = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(Json.obj("shares" -> List(Json.toJson(InputShare("cert", "read-only", TasksFilter.all, ObservablesFilter.all)))))
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
      val result3 = shareCtrl.shareCase("#4")(requestUpdate)

      status(result3) must equalTo(201)

      val l2     = getShares("#4")
      val share2 = l2.find(_.organisationName == "cert")

      share2 must beSome.which(s => {
        s.profileName shouldEqual "all"
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

    "handle share put/remove correctly" in {
      val requestOrga = FakeRequest("POST", "/api/v0/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description")))
        .withHeaders("user" -> "admin@thehive.local")
      val resultOrga = organisationCtrl.create(requestOrga)
      status(resultOrga) must beEqualTo(201)
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/default/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["orga1", "cert"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("default")(requestBulkLink)

      status(resultBulkLink) shouldEqual 201

      val request = FakeRequest("POST", s"/api/case/#3/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        .withJsonBody(
          Json.obj(
            "shares" -> List(
              Json.toJson(InputShare("cert", "read-only", TasksFilter.all, ObservablesFilter.all)),
              Json.toJson(InputShare("orga1", "all", TasksFilter.none, ObservablesFilter.all))
            )
          )
        )
      val result = shareCtrl.shareCase("#3")(request)

      status(result) shouldEqual 201
      getShares("#3").length shouldEqual 3

      val requestEmpty = FakeRequest("POST", s"/api/case/#3/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        .withJsonBody(Json.obj("shares" -> JsArray.empty))
      val resultEmpty = shareCtrl.shareCase("#3")(requestEmpty)

      status(resultEmpty) shouldEqual 201

      val l = getShares("#3")

      l.length shouldEqual 3

      val requestFailRemove = FakeRequest("DELETE", s"/api/case/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        .withJsonBody(Json.obj("ids" -> l.filter(_.organisationName == "default").map(_._id)))
      val resultFailRemove = shareCtrl.removeShares()(requestFailRemove)

      status(resultFailRemove) shouldEqual 500
      getShares("#3").length shouldEqual 3

      val requestRemove = FakeRequest("DELETE", s"/api/case/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "default")
        .withJsonBody(Json.obj("ids" -> l.filterNot(_.organisationName == "default").map(_._id)))
      val resultRemove = shareCtrl.removeShares()(requestRemove)

      status(resultRemove) shouldEqual 204
    }

    "remove a share" in {
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["default"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("cert")(requestBulkLink)

      status(resultBulkLink) shouldEqual 201

      val request = FakeRequest("POST", s"/api/case/#1/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(
          Json.obj(
            "shares" -> List(
              Json.toJson(InputShare("default", "read-only", TasksFilter.all, ObservablesFilter.all))
            )
          )
        )
      val result = shareCtrl.shareCase("#1")(request)

      status(result) shouldEqual 201
      getShares("#1").length shouldEqual 2

      val share = getShares("#1").find(_.organisationName == "default").get

      val requestRemove = FakeRequest("DELETE", s"/api/case/share/${share._id}")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val resultRemove = shareCtrl.removeShare(share._id)(requestRemove)

      status(resultRemove) shouldEqual 204

      val ownShare = getShares("#1").find(_.organisationName == "cert").get
      val requestRemoveOwn = FakeRequest("DELETE", s"/api/case/share/${ownShare._id}")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val resultRemoveOwn = shareCtrl.removeShare(ownShare._id)(requestRemoveOwn)

      status(resultRemoveOwn) shouldEqual 500
    }

    "patch a share" in {
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["default"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("cert")(requestBulkLink)

      status(resultBulkLink) shouldEqual 201

      val request = FakeRequest("POST", s"/api/case/#2/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(
          Json.obj(
            "shares" -> List(
              Json.toJson(InputShare("default", "read-only", TasksFilter.all, ObservablesFilter.all))
            )
          )
        )
      val result = shareCtrl.shareCase("#2")(request)

      status(result) shouldEqual 201

      val l = getShares("#2")

      l.length shouldEqual 2

      val share = l.find(s => s.organisationName == "default" && s.profileName == "read-only")

      share must beSome
      l.find(s => s.organisationName == "default" && s.profileName == "all") must beNone

      val requestPatch = FakeRequest("PATCH", s"/api/case/share/${share.get._id}")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.parse("""{"profile": "all"}"""))
      val resultPatch = shareCtrl.updateShare(share.get._id)(requestPatch)

      status(resultPatch) shouldEqual 200

      val newL = getShares("#2")

      newL.length shouldEqual 2
      newL.find(s => s.organisationName == "default" && s.profileName == "all") must beSome
    }

    "fetch observable shares" in db.roTransaction { implicit graph =>
      val observables = caseSrv
        .get("#1")
        .observables(DummyUserSrv(userId = "user1@thehive.local", organisation = "cert", permissions = Permissions.all).authContext)
        .toList

      observables must not(beEmpty)

      val observableHfr = observables.find(_.message.contains("Some weird domain"))

      observableHfr must beSome

      val request = FakeRequest("GET", s"/api/case/#1/observable/${observableHfr.get._id}/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val result = shareCtrl.listShareObservables("#1", observableHfr.get._id)(request)

      status(result) shouldEqual 200
      contentAsJson(result).as[List[OutputShare]].length shouldEqual 1
    }
  }

}
