package org.thp.thehive.services.notification.triggers

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.models.Alert
import org.thp.thehive.services.{TestAppBuilder, TheHiveOpsNoDeps}
import play.api.test.PlaySpecification

import java.util.Date
import scala.util.Success

class AlertCreatedTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "alert created trigger" should {
    "be properly triggered on alert creation" in testApp { app =>
      import app._
      import app.thehiveModule._

      val alert = database.tryTransaction { implicit graph =>
        val certOrganisation = organisationSrv.getByName("cert").getOrFail("Organisation").get
        alertSrv.create(
          Alert(
            `type` = "test",
            source = "alert_creation_test",
            sourceRef = "#1",
            externalLink = None,
            title = "alert title (create alert test)",
            description = "alert description (create alert test)",
            severity = 2,
            date = new Date(),
            lastSyncDate = new Date(),
            tags = Seq("tag1", "tag2"),
            tlp = 1,
            pap = 3,
            read = false,
            follow = true
          ),
          certOrganisation,
          Set("tag1", "tag2"),
          Nil,
          None
        )
      }

      database.roTransaction { implicit graph =>
        alert must beSuccessfulTry

        val audit = auditSrv.startTraversal.has(_.objectId, alert.get._id.toString).getOrFail("Audit")

        audit must beSuccessfulTry

        val organisation = organisationSrv.get(EntityName("cert")).getOrFail("Organisation")

        organisation must beSuccessfulTry

        val user2 = userSrv.getOrFail(EntityName("certadmin@thehive.local"))
        val user1 = userSrv.getOrFail(EntityName("certuser@thehive.local"))

        user2 must beSuccessfulTry
        user1 must beSuccessfulTry

        val alertCreated = AlertCreated

        alertCreated.filter(audit.get, Some(alert.get.alert), organisation.get, user1.toOption) must beFalse
        alertCreated.filter(audit.get, Some(alert.get.alert), organisation.get, user2.toOption) must beTrue
        Success(())
      } must beASuccessfulTry
    }
  }
}
