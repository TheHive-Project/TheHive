package org.thp.thehive.connector.cortex.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{AlertSrv, ObservableSrv, TaskSrv}

class EntityHelperTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all).authContext
  "entity helper" should {

    "return task info" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        for {
          task              <- app[TaskSrv].initSteps.has("title", "case 1 task 1").getOrFail()
          (title, tlp, pap) <- app[EntityHelper].entityInfo(task)
        } yield (title, tlp, pap)
      } must beASuccessfulTry.which {
        case (title, tlp, pap) =>
          title must beEqualTo("")
          tlp must beEqualTo(2)
          pap must beEqualTo(2)
      }
    }

    "return observable info" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        for {
          observable        <- app[ObservableSrv].initSteps.has("title", "case 1 task 1").getOrFail() // FIXME
          (title, tlp, pap) <- app[EntityHelper].entityInfo(observable)
        } yield (title, tlp, pap)
      } must beASuccessfulTry.which {
        case (title, tlp, pap) =>
          title must beEqualTo("")
          tlp must beEqualTo(2)
          pap must beEqualTo(2)
      }
    }

    "find a manageable entity only (task)" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        for {
          task <- app[TaskSrv].initSteps.has("title", "case 1 task 1").getOrFail()
          t    <- app[EntityHelper].get("Task", task._id, Permissions.manageAction)
        } yield t
      } must beSuccessfulTry
    }

    "find a manageable entity only (alert)" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref2")
          t     <- app[EntityHelper].get("Alert", alert._id, Permissions.manageAction)
        } yield t
      } must beSuccessfulTry
    }
  }
}
