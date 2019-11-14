package org.thp.thehive.services

import java.util.Date

import gremlin.scala.Graph
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.Try

class AlertSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val alertSrv: AlertSrv                = app.instanceOf[AlertSrv]
    val db: Database                      = app.instanceOf[Database]
    val orgaSrv                           = app.instanceOf[OrganisationSrv]
    val tagSrv                            = app.instanceOf[TagSrv]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    def createAlert(tp: String, title: String, description: String, tags: Set[String])(implicit graph: Graph) = alertSrv.create(
      Alert(
        tp,
        "#1",
        "alert_creation_test",
        Some("test.com"),
        title,
        description,
        0,
        new Date(),
        new Date(),
        1,
        2,
        read = false,
        follow = false
      ),
      orgaSrv.getOrFail("cert").get,
      tags,
      Map("float1" -> Some(2.3.toFloat)),
      None
    )

    s"[$name] alert service" should {
      "create an alert" in {
        val a = db.tryTransaction(
          implicit graph => createAlert("test", "test", "test desc", Set("tag1", "tag2"))
        )
        a must beSuccessfulTry.which(a => {
          a.title shouldEqual "test"
          a.source shouldEqual "#1"
          a.sourceRef shouldEqual "alert_creation_test"
          a.externalLink shouldEqual Some("test.com")
          a.description shouldEqual "test desc"
          a.severity shouldEqual 0
          a.tlp shouldEqual 1
          a.pap shouldEqual 2
        })

        db.roTransaction { implicit graph =>
          orgaSrv.get("cert").alerts.toList must contain(a.get.alert)

          val tags = tagSrv.initSteps.toList.filter(t => t.predicate == "tag1" || t.predicate == "tag2")

          alertSrv.get(a.get.alert).tags.toList must containTheSameElementsAs(tags)
        }
      }

      "update tags" in {
        // Get some data first
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 2", "test 2", "test desc 2", Set("tag3", "tag4"))
          )
          .get
        val tag3 = db.roTransaction { implicit graph =>
          tagSrv.initSteps.toList.filter(t => t.predicate == "tag3")
        }.head
        val tag5 = db
          .tryTransaction(
            implicit graph => tagSrv.create(Tag("_autocreate", "tag5", None, None, 0))
          )
          .get

        // Test updateTags
        val r = db.tryTransaction { implicit graph =>
          alertSrv.updateTags(a.alert, Set(tag3, tag5))
        }
        r must beSuccessfulTry
        db.roTransaction { implicit graph =>
          alertSrv.get(a.alert).tags.toList must contain(exactly(tag3, tag5))
        }

        // Test updateTagNames
        val r2 = db.tryTransaction { implicit graph =>
          alertSrv.updateTagNames(a.alert, Set("tag5", "tag6"))
        }
        r2 must beSuccessfulTry
        val tag6 = db.roTransaction { implicit graph =>
          tagSrv.initSteps.toList.filter(t => t.predicate == "tag6")
        }.head
        db.roTransaction { implicit graph =>
          alertSrv.get(a.alert).tags.toList must contain(exactly(tag5, tag6))
        }
      }
    }
  }
}
