package org.thp.thehive.connector.cortex.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.connector.cortex.TestAppBuilder
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{TheHiveOps, TheHiveOpsNoDeps}
import play.api.test.PlaySpecification

class EntityHelperTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certadmin@thehive.local", organisation = "cert", permissions = Permissions.all).authContext
  "entity helper" should {

    "return task info" in testApp { app =>
      import app._
      import app.cortexModule._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        for {
          task              <- taskSrv.startTraversal.has(_.title, "case 1 task 1").getOrFail("Task")
          (title, tlp, pap) <- entityHelper.entityInfo(task)
        } yield (title, tlp, pap)
      } must beASuccessfulTry.which {
        case (title, tlp, pap) =>
          title must beEqualTo("case 1 task 1 (Waiting)")
          tlp   must beEqualTo(2)
          pap   must beEqualTo(2)
      }
    }

    "return observable info" in testApp { app =>
      import app._
      import app.cortexModule._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        for {
          observable        <- observableSrv.startTraversal.has(_.message, "Some weird domain").getOrFail("Observable")
          (title, tlp, pap) <- entityHelper.entityInfo(observable)
        } yield (title, tlp, pap)
      } must beASuccessfulTry.which {
        case (title, tlp, pap) =>
          title must beEqualTo("[domain] h.fr")
          tlp   must beEqualTo(3)
          pap   must beEqualTo(2)
      }
    }

    "find a manageable entity only (task)" in testApp { app =>
      import app._
      import app.cortexModule._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        for {
          task <- taskSrv.startTraversal.has(_.title, "case 1 task 1").getOrFail("Task")
          t    <- entityHelper.get("Task", task._id, Permissions.manageAction)
        } yield t
      } must beSuccessfulTry
    }

    "find a manageable entity only (alert)" in testApp { app =>
      import app._
      import app.cortexModule._
      import app.thehiveModule._

      TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
        import ops.AlertOpsDefs

        database.roTransaction { implicit graph =>
          for {
            alert <- alertSrv.get(EntityName("testType;testSource;ref2")).visible.getOrFail("Alert")
            t     <- entityHelper.get("Alert", alert._id, Permissions.manageAction)
          } yield t
        } must beSuccessfulTry
      }
    }
  }
}
