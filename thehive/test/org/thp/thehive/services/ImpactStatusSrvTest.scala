package org.thp.thehive.services

import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.ImpactStatusOps._
import play.api.test.PlaySpecification

class ImpactStatusSrvTest extends PlaySpecification with TestAppBuilder {
  "impact status service" should {
    "get values" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[ImpactStatusSrv].startTraversal.toSeq must containTheSameElementsAs(
          Seq(
            ImpactStatus("NoImpact"),
            ImpactStatus("WithImpact"),
            ImpactStatus("NotApplicable")
          )
        )

        app[ImpactStatusSrv].startTraversal.getByName("NoImpact").exists must beTrue
      }
    }
  }
}
