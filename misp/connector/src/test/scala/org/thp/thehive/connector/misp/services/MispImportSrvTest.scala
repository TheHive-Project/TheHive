package org.thp.thehive.connector.misp.services

import akka.stream.scaladsl.Sink
import org.thp.misp.dto.{Event, Organisation, Tag, User}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.{EntityId}
import org.thp.thehive.connector.misp.TestAppBuilder
import org.thp.thehive.models.{Alert, Permissions}
import org.thp.thehive.services.{TheHiveOps, TheHiveOpsNoDeps}
import play.api.test.PlaySpecification

import java.util.{Date, UUID}
import scala.concurrent.duration.DurationInt

class MispImportSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  sequential

  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext
//  override def appConfigure: AppBuilder =
//    super
//      .appConfigure
//      .bindToProvider[TheHiveMispClient, TestMispClientProvider]

  "MISP client" should {
    "get current user name" in testApp { app =>
      import app._
      import app.mispModule._

      await(theHiveMispClient.getCurrentUser) must beEqualTo(User("1", "1", "admin@admin.test"))
    }

    "get organisation" in testApp { app =>
      import app._
      import app.mispModule._

      await(theHiveMispClient.getOrganisation("1")) must beEqualTo(
        Organisation("1", "ORGNAME", Some("Automatically generated admin organisation"), UUID.fromString("5d5d066f-cfa4-49da-995c-6d5b68257ab4"))
      )
    }

    "get current organisation" in testApp { app =>
      import app.mispModule._

      theHiveMispClient.currentOrganisationName must beSuccessfulTry("ORGNAME")
    }

    "retrieve events" in testApp { app =>
      import app._
      import app.mispModule._

      val events = theHiveMispClient
        .searchEvents(None)
        .runWith(Sink.seq)(materializer)
      val e = await(events)
      Seq(1, 2, 3) must contain(2)
      e must contain(
        Event(
          id = "1",
          published = true,
          info = "test1 -> 1.2",
          threatLevel = Some(1),
          analysis = Some(1),
          date = Event.simpleDateFormat.parse("2019-08-23"),
          publishDate = new Date(1566913355000L),
          org = "ORGNAME",
          orgc = "ORGNAME",
          attributeCount = Some(11),
          distribution = 1,
          attributes = Nil,
          tags = Seq(Tag(Some("1"), "TH-test", Some("#36a3a3"), None), Tag(Some("2"), "TH-test-2", Some("#1ac7c7"), None))
        )
      )
    }
  }

  "MISP service" should {
    "import events" in testApp { app =>
      import app._
      import app.mispModule._
      import app.thehiveModule._

      TheHiveOps(organisationSrv, customFieldSrv) { ops =>
        import ops.AlertOpsDefs

        database.roTransaction { implicit graph =>
          mispImportSrv.syncMispEvents(theHiveMispClient)
          alertSrv.startTraversal.getBySourceId("misp", "ORGNAME", "1").visible.getOrFail("Alert")
        } must beSuccessfulTry
          .which { alert: Alert =>
            alert must beEqualTo(
              Alert(
                `type` = "misp",
                source = "ORGNAME",
                sourceRef = "1",
                externalLink = Some("https://misp.test/events/1"),
                title = "#1 test1 -> 1.2",
                description = s"Imported from MISP Event #1, created at ${Event.simpleDateFormat.parse("2019-08-23")}",
                severity = 3,
                date = Event.simpleDateFormat.parse("2019-08-23"),
                lastSyncDate = new Date(1566913355000L),
                tlp = 2,
                pap = 2,
                read = false,
                follow = true,
                tags = Seq("src:ORGNAME", "TH-test", "TH-test-2"),
                organisationId = alert.organisationId,
                caseId = EntityId.empty
              )
            )
          }
          .eventually(5, 100.milliseconds)
      }

      val observables = database
        .roTransaction { implicit graph =>
          alertSrv
            .startTraversal
            .getBySourceId("misp", "ORGNAME", "1")
            .observables
            .richObservable
            .toList
        }
        .map(o => (o.dataType, o.data, o.tlp, o.message, o.tags.toSet))
      observables must contain(
        ("filename", Some("plop"), 0, Some(""), Set("TH-test", "misp.category=\"Artifacts dropped\"", "misp.type=\"filename\""))
      )
    }
  }
}
