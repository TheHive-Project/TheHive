package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.models.Organisation
import org.thp.thehive.services.{TestAppBuilder => _}
import play.api.test.PlaySpecification

class ServiceHelperTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  "service helper" should {
    "filter properly organisations according to supplied config" in testApp { app =>
      import app._
      import app.cortexModule._
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
      import app.cortexModule._

      val r = serviceHelper.availableCortexClients(Seq(cortexClient), EntityName(Organisation.administration.name))

      r must contain(cortexClient)
    }
  }
}
