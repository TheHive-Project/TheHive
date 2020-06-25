package org.thp.thehive.services

import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

class ImpactStatusSrvTest extends PlaySpecification with TestAppBuilder {
  "impact status service" should {
    "get values" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[ImpactStatusSrv].initSteps.toList must containTheSameElementsAs(
          Seq(
            ImpactStatus("NoImpact"),
            ImpactStatus("WithImpact"),
            ImpactStatus("NotApplicable")
          )
        )

        app[ImpactStatusSrv].initSteps.getByName("NoImpact").exists() must beTrue
      }
    }
  }
}
