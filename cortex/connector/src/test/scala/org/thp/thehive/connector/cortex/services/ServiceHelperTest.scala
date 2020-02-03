package org.thp.thehive.connector.cortex.services

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Organisation
import org.thp.thehive.services._
import play.api.test.PlaySpecification

import org.thp.scalligraph.AppBuilder
import org.thp.thehive.connector.cortex.models.TheHiveCortexSchemaProvider

class ServiceHelperTest extends PlaySpecification with TestAppBuilder {
  override val databaseName: String = "thehiveCortex"
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])
      .`override`(
        _.bindActor[CortexActor]("cortex-actor")
          .bindToProvider[CortexClient, TestCortexClientProvider]
          .bind[Connector, TestConnector]
          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
      )

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
      val r      = app[ServiceHelper].availableCortexClients(Seq(client), OrganisationSrv.administration.name)

      r must contain(client)
    }
  }
}
