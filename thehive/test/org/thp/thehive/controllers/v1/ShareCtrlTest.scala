package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.{EntityIdOrName, EntityName}
import org.thp.thehive.TestApplication
import org.thp.thehive.dto.v1.{OutputObservable, OutputTask}
import org.thp.thehive.services.{TheHiveOps, WithTheHiveModule}
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class ShareCtrlTest extends PlaySpecification with TestAppBuilder {

  def testTaskSharingRule(
      sharingRule: String
  )(implicit app: TestApplication with WithTheHiveModule with WithTheHiveModuleV1): Seq[String] = {
    import app.thehiveModule._
    import app.thehiveModuleV1._

    TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
      import ops._

      app
        .database
        .tryTransaction { implicit graph =>
          implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext

          caseSrv.getByName("2").share(EntityName("cert")).update(_.taskRule, sharingRule).getOrFail("Case")
        }
        .get

      val request = FakeRequest("POST", "/api/v1/task")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.obj("caseId" -> "2", "title" -> "shared task"))
      val result = taskCtrl.create()(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val taskId = contentAsJson(result).as[OutputTask]._id

      app.database.roTransaction { implicit graph =>
        taskSrv.get(EntityIdOrName(taskId)).organisations.value(_.name).toSeq
      }
    }
  }

  def testObservableSharingRule(
      sharingRule: String
  )(implicit app: TestApplication with WithTheHiveModule with WithTheHiveModuleV1): Seq[String] = {
    import app.thehiveModule._
    import app.thehiveModuleV1._

    TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
      import ops._

      app
        .database
        .tryTransaction { implicit graph =>
          implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext

          caseSrv.getByName("2").share(EntityName("cert")).update(_.observableRule, sharingRule).getOrFail("Case")
        }
        .get

      val request = FakeRequest("POST", "/api/v1/case/2/observable")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.obj("data" -> "99.99.99.99", "dataType" -> "ip"))
      val result = observableCtrl.createInCase("2")(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val observableId = contentAsJson(result).as[Seq[OutputObservable]].head._id

      app.database.roTransaction { implicit graph =>
        observableSrv.get(EntityIdOrName(observableId)).organisations.value(_.name).toSeq
      }
    }
  }

  "share controller" should {
    "apply sharing rule (all) when a task is created" in testApp { implicit app =>
      testTaskSharingRule("all") must contain(exactly("soc", "cert"))
    }
    "apply sharing rule (none) when a task is created" in testApp { implicit app =>
      testTaskSharingRule("none") must contain(exactly("cert"))
    }
    "apply sharing rule (existingOnly) when a task is created" in testApp { implicit app =>
      testTaskSharingRule("existingOnly") must contain(exactly("cert"))
    }
    "apply sharing rule (upcomingOnly) when a task is created" in testApp { implicit app =>
      testTaskSharingRule("upcomingOnly") must contain(exactly("soc", "cert"))
    }
    "apply sharing rule (all) when a observable is created" in testApp { implicit app =>
      testObservableSharingRule("all") must contain(exactly("soc", "cert"))
    }
    "apply sharing rule (none) when a observable is created" in testApp { implicit app =>
      testObservableSharingRule("none") must contain(exactly("cert"))
    }
    "apply sharing rule (existingOnly) when a observable is created" in testApp { implicit app =>
      testObservableSharingRule("existingOnly") must contain(exactly("cert"))
    }
    "apply sharing rule (upcomingOnly) when a observable is created" in testApp { implicit app =>
      testObservableSharingRule("upcomingOnly") must contain(exactly("soc", "cert"))
    }

  }
}
