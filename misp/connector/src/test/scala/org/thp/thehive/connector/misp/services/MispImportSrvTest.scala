package org.thp.thehive.connector.misp.services

import java.awt.Color
import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.Try

import play.api.test.PlaySpecification

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.misp.dto.{Event, Organisation, Tag, User}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Alert, DatabaseBuilder, Permissions}
import org.thp.thehive.services.{AlertSrv, OrganisationSrv}

class MispImportSrvTest(implicit ec: ExecutionContext) extends PlaySpecification with Mockito {
  val dummyUserSrv                    = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  implicit val sys: ActorSystem       = ActorSystem("MispTest")
  implicit val mat: ActorMaterializer = ActorMaterializer()

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app = TestAppBuilder(dbProvider)
      .bindToProvider[TheHiveMispClient, TestMispClientProvider]
//          .bind[Connector, TestConnector]

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
//    specs(dbProvider.name, app)
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val client = app.instanceOf[TheHiveMispClient]
    s"[$name] MISP client" should {

      "get current user name" in {
        await(client.getCurrentUser) must beEqualTo(User("1", "1", "admin@admin.test"))
      }

      "get organisation" in {
        await(client.getOrganisation("1")) must beEqualTo(
          Organisation("1", "ORGNAME", "Automatically generated admin organisation", UUID.fromString("5d5d066f-cfa4-49da-995c-6d5b68257ab4"))
        )
      }

      "get current organisation" in {
        client.currentOrganisationName must beSuccessfulTry("ORGNAME")
      }

      "retrieve events" in {
        val events = client
          .searchEvents(None)
          .runWith(Sink.seq)
        val e = await(events)
        println(e)
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
            tags = Seq(Tag(Some("1"), "TH-test", Some(new Color(0x36a3a3)), None), Tag(Some("2"), "TH-test-2", Some(new Color(0x1ac7c7)), None))
          )
        )
      }
    }
    "MISP service " should {
      val mispImportSrv   = app.instanceOf[MispImportSrv]
      val organisationSrv = app.instanceOf[OrganisationSrv]
      val alertSrv        = app.instanceOf[AlertSrv]
      val db              = app.instanceOf[Database]
      "import events" in {

        await(mispImportSrv.syncMispEvents(client)(dummyUserSrv.getSystemAuthContext))(1.minute)
        db.roTransaction { implicit graph =>
          alertSrv.initSteps.getBySourceId("misp", "ORGNAME", "1").getOrFail()
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
            flag = false,
            tlp = 2,
            pap = 2,
            read = false,
            follow = true
          )
        )

        val observables = db
          .roTransaction { implicit graph =>
            organisationSrv
              .get("default")
              .alerts
              .getBySourceId("misp", "ORGNAME", "1")
              .observables
              .richObservable
              .toList
          }
          .map(o => (o.`type`.name, o.data.map(_.data), o.tlp, o.message, o.tags.map(_.name).toSet))
        println(observables.mkString("\n"))
        observables must contain(
          ("filename", Some("plop"), 0, Some(""), Set("TH-test", "MISP:category=Artifacts dropped", "MISP:type=filename"))
        )
      }
    }
  }

}
