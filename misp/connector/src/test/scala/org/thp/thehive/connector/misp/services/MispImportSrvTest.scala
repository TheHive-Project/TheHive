package org.thp.thehive.connector.misp.services

import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import play.api.test.PlaySpecification

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.thp.misp.dto.{Event, Organisation, Tag, User}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Alert, Permissions}
import org.thp.thehive.services.{AlertSrv, OrganisationSrv}

class MispImportSrvTest(implicit ec: ExecutionContext) extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Permissions.all).authContext
  override val appConfigure: AppBuilder = super
    .appConfigure
    .bindToProvider[TheHiveMispClient, TestMispClientProvider]

  "MISP client" should {
    "get current user name" in testApp { app =>
      await(app[TheHiveMispClient].getCurrentUser) must beEqualTo(User("1", "1", "admin@admin.test"))
    }

    "get organisation" in testApp { app =>
      await(app[TheHiveMispClient].getOrganisation("1")) must beEqualTo(
        Organisation("1", "ORGNAME", "Automatically generated admin organisation", UUID.fromString("5d5d066f-cfa4-49da-995c-6d5b68257ab4"))
      )
    }

    "get current organisation" in testApp { app =>
      app[TheHiveMispClient].currentOrganisationName must beSuccessfulTry("ORGNAME")
    }

    "retrieve events" in testApp { app =>
      val events = app[TheHiveMispClient]
        .searchEvents(None)
        .runWith(Sink.seq)(app[Materializer])
      val e = await(events)
//        println(e)
      Seq(1, 2, 3) must contain(2)
      e must contain(
        Event(
          id = "1",
          published = true,
          info = "test1 -> 1.2",
          threatLevel = Some(1),
          analysis = Some(1),
          date = new Date(1566511200000L),
          publishDate = new Date(1566913355000L),
          org = "ORGNAME",
          orgc = "ORGNAME",
          attributeCount = Some(11),
          distribution = 1,
          attributes = Nil,
          tags = Seq(Tag(Some("1"), "TH-test", Some(0x36a3a3), None), Tag(Some("2"), "TH-test-2", Some(0x1ac7c7), None))
        )
      )
    }
  }

  "MISP service " should {
    // FIXME sometimes it fails ?!
    "import events" in testApp { app =>
      val x = await(app[MispImportSrv].syncMispEvents(app[TheHiveMispClient])(authContext))(1.minute)

      app[Database].roTransaction { implicit graph =>
        app[AlertSrv].initSteps.getBySourceId("misp", "ORGNAME", "1").getOrFail()
      } must beSuccessfulTry(
        Alert(
          `type` = "misp",
          source = "ORGNAME",
          sourceRef = "1",
          externalLink = Some("https://misp.test/events/1"),
          title = "#1 test1 -> 1.2",
          description = "Imported from MISP Event #1, created at Fri Aug 23 00:00:00 CEST 2019",
          severity = 3,
          date = new Date(1566511200000L),
          lastSyncDate = new Date(1566913355000L),
          tlp = 2,
          pap = 2,
          read = false,
          follow = true
        )
      )

      val observables = app[Database]
        .roTransaction { implicit graph =>
          app[OrganisationSrv]
            .get("admin")
            .alerts
            .getBySourceId("misp", "ORGNAME", "1")
            .observables
            .richObservable
            .toList
        }
        .map(o => (o.`type`.name, o.data.map(_.data), o.tlp, o.message, o.tags.map(_.toString).toSet))
//        println(observables.mkString("\n"))
      observables must contain(
        ("filename", Some("plop"), 0, Some(""), Set("TH-test", "misp.category=\"Artifacts dropped\"", "misp.type=\"filename\""))
      )
    }
  }
}
