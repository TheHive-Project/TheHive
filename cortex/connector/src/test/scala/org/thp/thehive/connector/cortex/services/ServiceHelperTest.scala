package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.models.Organisation
import org.thp.thehive.services.{TestAppBuilder => _}
import play.api.test.PlaySpecification

class ServiceHelperTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  override val databaseName: String = "thehiveCortex"
//  override def appConfigure: AppBuilder =
//    super
//      .appConfigure
////      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])
//      .`override`(
//        _.bindActor[CortexActor]("cortex-actor")
//          .bindToProvider[CortexClient, TestCortexClientProvider]
//          .bind[Connector, TestConnector]
////          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
//      )

  "service helper" should {
    "filter properly organisations according to supplied config" in testApp { app =>
      import app._
      import app.cortexConnector._
      import app.thehiveModule._

      val r = database.roTransaction { implicit graph =>
        serviceHelper
          .organisationFilter(
            organisationSrv.startTraversal,
            List("*"),
            List("cert")
          )
          .toList
      }
      r must contain(Organisation.administration)

      val r2 = database.roTransaction { implicit graph =>
        serviceHelper
          .organisationFilter(
            organisationSrv.startTraversal,
            Nil,
            Nil
          )
          .toList
      }
      r2 must contain(Organisation.administration, Organisation("cert", "cert"))
    }

    "return the correct filtered CortexClient list" in testApp { app =>
      import app.cortexConnector._

      val r = serviceHelper.availableCortexClients(Seq(cortexClient), EntityName(Organisation.administration.name))

      r must contain(cortexClient)
    }
  }
}
