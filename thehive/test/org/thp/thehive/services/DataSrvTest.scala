package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.models._
import play.api.test.PlaySpecification

class DataSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "data service" should {
    "create not existing data" in testApp { app =>
      import app._
      import app.thehiveModule._

      val existingData = database.roTransaction(implicit graph => dataSrv.startTraversal.getByData("h.fr").getOrFail("Data")).get
      val newData      = database.tryTransaction(implicit graph => dataSrv.create(existingData))
      newData must beSuccessfulTry.which(data => data._id shouldEqual existingData._id)
    }

    "get related observables" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        observableSrv.create(
          Observable(
            message = Some("love"),
            tlp = 1,
            ioc = false,
            sighted = true,
            ignoreSimilarity = None,
            dataType = "domain",
            tags = Seq("tagX")
          ),
          "love.com"
        )
      }

      database.roTransaction(implicit graph => dataSrv.startTraversal.getByData("love.com").observables.exists must beTrue)
    }
  }
}
