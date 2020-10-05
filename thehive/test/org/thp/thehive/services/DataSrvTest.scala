package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.DataOps._
import play.api.test.PlaySpecification

class DataSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "data service" should {
    "create not existing data" in testApp { app =>
      val existingData = app[Database].roTransaction(implicit graph => app[DataSrv].startTraversal.getByData("h.fr").getOrFail("Data")).get
      val newData      = app[Database].tryTransaction(implicit graph => app[DataSrv].create(existingData))
      newData must beSuccessfulTry.which(data => data._id shouldEqual existingData._id)
    }

    "get related observables" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[ObservableSrv].create(
          Observable(Some("love"), 1, ioc = false, sighted = true),
          app[ObservableTypeSrv].get(EntityName("domain")).getOrFail("Observable").get,
          "love.com",
          Set("tagX"),
          Nil
        )
      }

      app[Database].roTransaction(implicit graph => app[DataSrv].startTraversal.getByData("love.com").observables.exists must beTrue)
    }
  }
}
