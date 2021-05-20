package org.thp.thehive.services

import org.thp.thehive.models._
import play.api.test.PlaySpecification

class ImpactStatusSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  "impact status service" should {
    "get values" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        impactStatusSrv.startTraversal.toSeq must containTheSameElementsAs(
          Seq(
            ImpactStatus("NoImpact"),
            ImpactStatus("WithImpact"),
            ImpactStatus("NotApplicable")
          )
        )

        impactStatusSrv.startTraversal.getByName("NoImpact").exists must beTrue
      }
    }
  }
}
