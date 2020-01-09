package org.thp.thehive.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class DataSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "data service" should {
    "create not existing data" in testApp { app =>
      val existingData = app[Database].roTransaction(implicit graph => app[DataSrv].initSteps.has("data", "h.fr").getOrFail()).get
      val newData      = app[Database].tryTransaction(implicit graph => app[DataSrv].create(existingData))
      newData must beSuccessfulTry.which(data => data._id shouldEqual existingData._id)
    }

    "get related observables" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[ObservableSrv].create(
          Observable(Some("love"), 1, ioc = false, sighted = true),
          app[ObservableTypeSrv].get("domain").getOrFail().get,
          "love.com",
          Set("tagX"),
          Nil
        )
      }

      app[Database].roTransaction(implicit graph => app[DataSrv].initSteps.getByData("love.com").observables.exists() must beTrue)
    }
  }
}
