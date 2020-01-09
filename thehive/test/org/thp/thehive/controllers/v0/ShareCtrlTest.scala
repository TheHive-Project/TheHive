package org.thp.thehive.controllers.v0

import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0._
import org.thp.thehive.services.CaseSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class ShareCtrlTest extends PlaySpecification with TestAppBuilder {
//  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)

//    def getShares(caseId: String) = {
//      val requestGet = FakeRequest("GET", s"/api/case/$caseId/shares")
//        .withHeaders("user" -> "user2@thehive.local")
//      val resGet = app[ShareCtrl].listShareCases(caseId)(requestGet)
//
//      status(resGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resGet)}")
//
//      val l = contentAsJson(resGet).as[List[OutputShare]]
//
//      l
//    }
//
//    def getSomeShares(caseId: String, user: String, orga: String) = {
//      val requestGet = FakeRequest("GET", s"/api/case/$caseId/shares")
//        .withHeaders("user" -> s"$user@thehive.local")
//      val resGet = app[ShareCtrl].listShareCases(caseId)(requestGet)
//
//      status(resGet) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(resGet)}")
//
//      val l = contentAsJson(resGet).as[List[OutputShare]]
//
//      l
//    }
//
//    def getTaskShares(caseId: String, taskId: String, user: String, orga: String) = {
//      val request = FakeRequest("GET", s"""/api/case/$caseId/task/$taskId/shares""")
//        .withHeaders("user" -> s"$user@thehive.local")
//      val result = app[ShareCtrl].listShareTasks(caseId, taskId)(request)
//
//      status(result) shouldEqual 200
//
//      contentAsJson(result).as[List[OutputShare]]
//    }

  "share a case" in testApp { app =>
    val inputShare = Json.obj("shares" -> List(Json.toJson(InputShare("soc", "all", TasksFilter.all, ObservablesFilter.all))))

    val request = FakeRequest("POST", "/api/case/#1/shares")
      .withJsonBody(inputShare)
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].shareCase("#1")(request)

    status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

    app[Database].roTransaction { implicit graph =>
      app[CaseSrv].get("#1").visible(DummyUserSrv(userId = "socro@thehive.local").authContext).exists()
    } must beTrue
  }

  "fail to share a already share case" in testApp { app =>
    val inputShare = Json.obj("shares" -> List(Json.toJson(InputShare("soc", "all", TasksFilter.all, ObservablesFilter.all))))

    val request = FakeRequest("POST", "/api/case/#2/shares")
      .withJsonBody(inputShare)
      .withHeaders("user" -> "certuser@thehive.local")
    val result = app[ShareCtrl].shareCase("#2")(request)

    status(result) must equalTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
  }

//    "remove a share" in testApp { app =>
//      val requestRemove = FakeRequest("DELETE", s"/api/case/share/${share._id}")
//        .withHeaders("user" -> "user5@thehive.local")
//      val resultRemove = app[ShareCtrl].removeShare(share._id)(requestRemove)
//
//      status(resultRemove) must equalTo(204).updateMessage(s => s"$s\n${contentAsString(resultRemove)}")
//    }.pendingUntilFixed("not sure of 'else if (!shareSrv.get(shareId).byOrganisationName(authContext.organisation).exists())' in ShareCtrl")

//    "patch a share" in testApp { app =>
//      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
//        .withHeaders("user" -> "admin@thehive.local")
//        .withJsonBody(Json.parse("""{"organisations":["admin"]}"""))
//      val resultBulkLink = app[OrganisationCtrl].bulkLink("cert")(requestBulkLink)
//
//      status(resultBulkLink) must equalTo(201).updateMessage(s => s"$s\n${contentAsString(resultBulkLink)}")
//
//      val request = FakeRequest("POST", s"/api/case/#2/shares")
//        .withHeaders("user" -> "user5@thehive.local")
//        .withJsonBody(
//          Json.obj(
//            "shares" -> List(
//              Json.toJson(InputShare("admin", "read-only", TasksFilter.all, ObservablesFilter.all))
//            )
//          )
//        )
//      val result = app[ShareCtrl].shareCase("#2")(request)
//
//      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      val l = getSomeShares("#2", "user5", "cert")
//
//      l.length shouldEqual 1
//
//      val share = l.find(s => s.organisationName == "admin" && s.profileName == "read-only")
//
//      share must beSome
//      l.find(s => s.organisationName == "admin" && s.profileName == "all") must beNone
//
//      val requestPatch = FakeRequest("PATCH", s"/api/case/share/${share.get._id}")
//        .withHeaders("user" -> "user5@thehive.local")
//        .withJsonBody(Json.parse("""{"profile": "all"}"""))
//      val resultPatch = app[ShareCtrl].updateShare(share.get._id)(requestPatch)
//
//      status(resultPatch) shouldEqual 200
//
//      val newL = getSomeShares("#2", "user5", "cert")
//
//      newL.length shouldEqual 1
//      newL.find(s => s.organisationName == "admin" && s.profileName == "all") must beSome
//    }

  "fetch and remove observable shares" in testApp { app =>
//      app[Database].roTransaction { implicit graph =>
//      val observables = caseSrv
//        .get("#1")
//        .observables(DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext)
//        .toList
//
//      observables must not(beEmpty)
//
//      val observableHfr = observables.find(_.message.contains("Some weird domain"))
//
//      observableHfr must beSome
//
//      def getObsShares = {
//        val request = FakeRequest("GET", s"/api/case/#1/observable/${observableHfr.get._id}/shares")
//          .withHeaders("user" -> "user2@thehive.local")
//        val result = app[ShareCtrl].listShareObservables("#1", observableHfr.get._id)(request)
//
//        status(result) shouldEqual 200
//
//        contentAsJson(result).as[List[OutputShare]]
//      }
//
//      val l = getObsShares
//
//      l.length shouldEqual 1
//
//      val requestAdd = FakeRequest("POST", s"/api/case/observable/${observableHfr.get._id}/shares")
//        .withHeaders("user" -> "user2@thehive.local")
//        .withJsonBody(Json.obj("organisations" -> List("cert")))
//      val resultAdd = app[ShareCtrl].shareObservable(observableHfr.get._id)(requestAdd)
//
//      status(resultAdd) shouldEqual 204
//      getObsShares.length shouldEqual 1
//
//      val requestDel = FakeRequest("DELETE", s"/api/observable/shares")
//        .withHeaders("user" -> "user2@thehive.local")
//        .withJsonBody(Json.obj("ids" -> List(l.head._id)))
//      val resultDel = app[ShareCtrl].removeObservableShares()(requestDel)
//
//      status(resultDel) shouldEqual 204
//      getObsShares must beEmpty
    pending("app[ShareCtrl].removeObservableShares has been refactor, need to rewrite test")
  }

  "fetch, add and remove shares for a task" in testApp { app =>
//      app[Database].roTransaction { implicit graph =>
    // Create a case with a task first
//      val c = app[Database]
//        .tryTransaction(
//          implicit graph =>
//            caseSrv.create(
//              Case(0, "case audit", "desc audit", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
//              None,
//              orgaSrv.getOrFail("admin").get,
//              Set.empty,
//              Map.empty,
//              None,
//              Seq(Task("task 1 new case", "group 666", None, TaskStatus.Waiting, flag = false, None, None, 0, None) -> None)
//            )(graph, dummyUserSrv.authContext)
//        )
//        .get
//      val task4 = caseSrv.get(c._id).tasks(dummyUserSrv.getSystemAuthContext).toList.find(_.title == "task 1 new case")
//
//      def getTaskShares = {
//        val request = FakeRequest("GET", s"/api/case/${c._id}/task/${task4.get._id}/shares")
//          .withHeaders("user" -> "user5@thehive.local")
//        val result = app[ShareCtrl].listShareTasks(c._id, task4.get._id)(request)
//
//        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//        contentAsJson(result).as[List[OutputShare]]
//      }
//
//      val l = getTaskShares
//
//      l must not(beEmpty)
//
//      val requestAdd = FakeRequest("POST", s"/api/case/task/${task4.get._id}/shares")
//        .withHeaders("user" -> "certuser@thehive.local")
//        .withJsonBody(Json.obj("organisations" -> List("admin")))
//      val resultAdd = app[ShareCtrl].shareTask(task4.get._id)(requestAdd)
//
//      status(resultAdd) shouldEqual 204
//      getTaskShares.length shouldEqual l.length
//
//      val requestDel = FakeRequest("DELETE", s"/api/task/shares")
//        .withHeaders("user" -> "user5@thehive.local")
//        .withJsonBody(Json.obj("ids" -> List(l.head._id)))
//      val resultDel = app[ShareCtrl].removeTaskShares()(requestDel)
//
//      status(resultDel) shouldEqual 204
//      getTaskShares must beEmpty
    pending("app[ShareCtrl].removeTaskShares has been refactor, need to rewrite test")
  }
}
