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
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
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
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
      val resGet = shareCtrl.listShareCases(caseId)(requestGet)

      status(resGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resGet)}")

      val l = contentAsJson(resGet).as[List[OutputShare]]

      l
    }

    def getSomeShares(caseId: String, user: String, orga: String) = {
      val requestGet = FakeRequest("GET", s"/api/case/$caseId/shares")
        .withHeaders("user" -> s"$user@thehive.local", "X-Organisation" -> orga)
      val resGet = shareCtrl.listShareCases(caseId)(requestGet)

      status(resGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resGet)}")

      val l = contentAsJson(resGet).as[List[OutputShare]]

      l
    }

    def getTaskShares(caseId: String, taskId: String, user: String, orga: String) = {
      val request = FakeRequest("GET", s"""/api/case/$caseId/task/$taskId/shares""")
        .withHeaders("user" -> s"$user@thehive.local", "X-Organisation" -> orga)
      val result = shareCtrl.listShareTasks(caseId, taskId)(request)

      status(result) shouldEqual 200

      contentAsJson(result).as[List[OutputShare]]
    }

    "manage shares for a case" in {
      val inputShare = Json.obj("shares" -> List(Json.toJson(InputShare("cert", "all", TasksFilter.all, ObservablesFilter.all))))

      val request = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
      val result = shareCtrl.shareCase("#4")(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val requestAgain = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(inputShare)
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
      val result2 = shareCtrl.shareCase("#4")(requestAgain)

      status(result2) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result2)}")

      val l     = getShares("#4")
      val share = l.find(_.organisationName == "cert")

      share must beSome.which(s => {
        s.profileName shouldEqual "all"
        s.organisationName shouldEqual "cert"
      })

      val requestUpdate = FakeRequest("POST", "/api/case/#4/shares")
        .withJsonBody(Json.obj("shares" -> List(Json.toJson(InputShare("cert", "read-only", TasksFilter.all, ObservablesFilter.all)))))
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
      val result3 = shareCtrl.shareCase("#4")(requestUpdate)

      status(result3) must equalTo(201)

      val l2     = getShares("#4")
      val share2 = l2.find(_.organisationName == "cert")

      share2 must beSome.which(s => {
        s.profileName shouldEqual "all"
        s.organisationName shouldEqual "cert"
      })
    }

    "fetch, add and remove shares for a task" in db.roTransaction { implicit graph =>
      val tasks = caseSrv.get("#4").tasks(dummyUserSrv.authContext).toList

      tasks must not(beEmpty)

      val task4 = tasks.find(_.title == "case 4 task 1")

      task4 must beSome

      def getTaskShares = {
        val request = FakeRequest("GET", s"/api/case/#4/task/${task4.get._id}/shares")
          .withHeaders("user" -> "user4@thehive.local", "X-Organisation" -> "cert")
        val result = shareCtrl.listShareTasks("#4", task4.get._id)(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        contentAsJson(result).as[List[OutputShare]]
      }

      val l = getTaskShares

      l must not(beEmpty)

      val requestAdd = FakeRequest("POST", s"/api/case/task/${task4.get._id}/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.obj("organisations" -> List("admin")))
      val resultAdd = shareCtrl.shareTask(task4.get._id)(requestAdd)

      status(resultAdd) shouldEqual 204
      getTaskShares.length shouldEqual l.length

      val requestDel = FakeRequest("DELETE", s"/api/task/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.obj("ids" -> List(l.head._id)))
      val resultDel = shareCtrl.removeShareTasks()(requestDel)

      status(resultDel) shouldEqual 204
      getTaskShares must beEmpty
    }

    "handle share post/remove correctly" in db.roTransaction { implicit graph =>
      val requestOrga = FakeRequest("POST", "/api/v0/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description")))
        .withHeaders("user" -> "admin@thehive.local")
      val resultOrga = organisationCtrl.create(requestOrga)
      status(resultOrga) must beEqualTo(201)
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/admin/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["orga1", "cert"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("admin")(requestBulkLink)

      status(resultBulkLink) shouldEqual 201

      val request = FakeRequest("POST", s"/api/case/#3/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
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
      getShares("#3").length shouldEqual 2

      val tasks = caseSrv.get("#3").tasks(dummyUserSrv.authContext).toList
      tasks must not(beEmpty)
      val task6 = tasks.find(_.title == "case 3 task 2")
      task6 must beSome
      getTaskShares("#3", task6.get._id, "user2", "admin").filter(_.organisationName == "orga1") must beEmpty

      val requestAddTask = FakeRequest("POST", s"/api/case/task/${task6.get._id}/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.obj("organisations" -> List("orga1")))
      val resultAddTask = shareCtrl.shareTask(task6.get._id)(requestAddTask)

      status(resultAddTask) shouldEqual 204
      getTaskShares("#3", task6.get._id, "user2", "admin").filter(_.organisationName == "orga1") must not(beEmpty)

      val requestEmpty = FakeRequest("POST", s"/api/case/#3/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.obj("shares" -> JsArray.empty))
      val resultEmpty = shareCtrl.shareCase("#3")(requestEmpty)

      status(resultEmpty) shouldEqual 201

      val l = getShares("#3")

      getShares("#3").length shouldEqual 2

      val requestRemove = FakeRequest("DELETE", s"/api/case/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.obj("ids" -> l.filterNot(_.organisationName == "admin").map(_._id)))
      val resultRemove = shareCtrl.removeShares()(requestRemove)

      status(resultRemove) shouldEqual 204
    }

    "remove a share" in {
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["admin"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("cert")(requestBulkLink)

      status(resultBulkLink) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(resultBulkLink)}")

      val request = FakeRequest("POST", s"/api/case/#1/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(
          Json.obj(
            "shares" -> List(
              Json.toJson(InputShare("admin", "read-only", TasksFilter.all, ObservablesFilter.all))
            )
          )
        )
      val result = shareCtrl.shareCase("#1")(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      getSomeShares("#1", "user1", "cert").length shouldEqual 1

      val share = getSomeShares("#1", "user1", "cert").find(_.organisationName == "admin").get

      val requestRemove = FakeRequest("DELETE", s"/api/case/share/${share._id}")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
      val resultRemove = shareCtrl.removeShare(share._id)(requestRemove)

      status(resultRemove) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(resultRemove)}")
    }

    "patch a share" in {
      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["admin"]}"""))
      val resultBulkLink = organisationCtrl.bulkLink("cert")(requestBulkLink)

      status(resultBulkLink) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(resultBulkLink)}")

      val request = FakeRequest("POST", s"/api/case/#2/shares")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(
          Json.obj(
            "shares" -> List(
              Json.toJson(InputShare("admin", "read-only", TasksFilter.all, ObservablesFilter.all))
            )
          )
        )
      val result = shareCtrl.shareCase("#2")(request)

      status(result) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val l = getSomeShares("#2", "user1", "cert")

      l.length shouldEqual 1

      val share = l.find(s => s.organisationName == "admin" && s.profileName == "read-only")

      share must beSome
      l.find(s => s.organisationName == "admin" && s.profileName == "all") must beNone

      val requestPatch = FakeRequest("PATCH", s"/api/case/share/${share.get._id}")
        .withHeaders("user" -> "user1@thehive.local", "X-Organisation" -> "cert")
        .withJsonBody(Json.parse("""{"profile": "all"}"""))
      val resultPatch = shareCtrl.updateShare(share.get._id)(requestPatch)

      status(resultPatch) shouldEqual 200

      val newL = getSomeShares("#2", "user1", "cert")

      newL.length shouldEqual 1
      newL.find(s => s.organisationName == "admin" && s.profileName == "all") must beSome
    }

    "fetch and remove observable shares" in db.roTransaction { implicit graph =>
      val observables = caseSrv
        .get("#1")
        .observables(DummyUserSrv(userId = "user1@thehive.local", organisation = "cert", permissions = Permissions.all).authContext)
        .toList

      observables must not(beEmpty)

      val observableHfr = observables.find(_.message.contains("Some weird domain"))

      observableHfr must beSome

      def getObsShares = {
        val request = FakeRequest("GET", s"/api/case/#1/observable/${observableHfr.get._id}/shares")
          .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        val result = shareCtrl.listShareObservables("#1", observableHfr.get._id)(request)

        status(result) shouldEqual 200

        contentAsJson(result).as[List[OutputShare]]
      }

      val l = getObsShares

      l.length shouldEqual 1

      val requestAdd = FakeRequest("POST", s"/api/case/observable/${observableHfr.get._id}/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.obj("organisations" -> List("cert")))
      val resultAdd = shareCtrl.shareObservable(observableHfr.get._id)(requestAdd)

      status(resultAdd) shouldEqual 204
      getObsShares.length shouldEqual 1

      val requestDel = FakeRequest("DELETE", s"/api/observable/shares")
        .withHeaders("user" -> "user2@thehive.local", "X-Organisation" -> "admin")
        .withJsonBody(Json.obj("ids" -> List(l.head._id)))
      val resultDel = shareCtrl.removeShareObservables()(requestDel)

      status(resultDel) shouldEqual 204
      getObsShares must beEmpty
    }
  }

}
