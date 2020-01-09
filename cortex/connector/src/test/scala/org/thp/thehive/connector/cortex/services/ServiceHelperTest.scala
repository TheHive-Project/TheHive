package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.CortexClient
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Organisation
import org.thp.thehive.services._
import play.api.test.PlaySpecification

class ServiceHelperTest extends PlaySpecification with TestAppBuilder {
  "service helper" should {
    "filter properly organisations according to supplied config" in testApp { app =>
      val r = app[Database].roTransaction { implicit graph =>
        app[ServiceHelper]
          .organisationFilter(
            app.apply[OrganisationSrv].initSteps,
            List("*"),
            List("cert")
          )
          .toList
      }
      r must contain(OrganisationSrv.administration)

      val r2 = app[Database].roTransaction { implicit graph =>
        app[ServiceHelper]
          .organisationFilter(
            app.apply[OrganisationSrv].initSteps,
            Nil,
            Nil
          )
          .toList
      }
      r2 must contain(OrganisationSrv.administration, Organisation("cert", "cert"))
    }

    "return the correct filtered CortexClient list" in testApp { app =>
      val client = app[CortexClient]
      val r      = app[ServiceHelper].availableCortexClients(Seq(app[CortexClient]), OrganisationSrv.administration.name)

      r must contain(client)
    }
  }
}
