package org.thp.thehive.services

import org.thp.scalligraph.traversal.TraversalOps._

import org.thp.thehive.models._
import org.thp.thehive.services.ImpactStatusOps._
import play.api.test.PlaySpecification

class ImpactStatusSrvTest extends PlaySpecification with TestAppBuilder {
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
