package org.thp.thehive.services

import java.util.Date

import gremlin.scala.Graph
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AppBuilder, CreateError}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.JsString
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
    val observableSrv                     = app.instanceOf[ObservableSrv]
    val observableTypeSrv                 = app.instanceOf[ObservableTypeSrv]
    val customFieldSrv                    = app.instanceOf[CustomFieldSrv]
    val caseTemplateSrv                   = app.instanceOf[CaseTemplateSrv]
    val caseSrv                           = app.instanceOf[CaseSrv]
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
      Map("string1" -> Some("lol")),
      Some(caseTemplateSrv.initSteps.get("spam").richCaseTemplate.getOrFail().get)
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

      "add tags" in {
        // Get some data first
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 3", "test 3", "test desc 3", Set("tag7", "tag8"))
          )
          .get
        val tag9 = db
          .tryTransaction(
            implicit graph => tagSrv.create(Tag("_autocreate", "tag9", None, None, 0))
          )
          .get

        // Test addTags
        val r = db.tryTransaction { implicit graph =>
          alertSrv.addTags(a.alert, Set("tag7", tag9.predicate))
        }
        r must beSuccessfulTry
        db.roTransaction { implicit graph =>
          alertSrv.get(a.alert).tags.toList.map(_.predicate) must contain(exactly("tag7", "tag9", "tag8"))
        }
      }

      "add an observable if not existing" in {
        // Get some data first
        val alert = db
          .tryTransaction(
            implicit graph => createAlert("test 4", "test 4", "test desc 4", Set.empty)
          )
          .get
        val alert4 = db.roTransaction { implicit graph =>
          alertSrv.initSteps.has("description", "description of alert #4").getOrFail()
        }.get
        val similarObs = db
          .tryTransaction(
            implicit graph => {
              observableSrv.create(
                Observable(Some("if you are lost"), 1, ioc = false, sighted = true),
                observableTypeSrv.get("domain").getOrFail().get,
                "perdu.com",
                Set("tag10"),
                Nil
              )
            }
          )
          .get

        // Test addObservable
        db.tryTransaction { implicit graph =>
          alertSrv.addObservable(alert4, similarObs)
        }.get must throwA[CreateError]

        val r = db.tryTransaction { implicit graph =>
          alertSrv.addObservable(alert.alert, similarObs)
        }
        r must beSuccessfulTry
        db.roTransaction { implicit graph =>
          alertSrv.get(alert.alert).observables.toList must contain(similarObs.observable)
        }
      }

      "update custom fields" in {
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 5", "test 5", "test desc 5", Set.empty)
          )
          .get

        val cfv = db.roTransaction { implicit graph =>
          customFieldSrv.getOrFail("string1").get
        }
        val r = db.tryTransaction(implicit graph => alertSrv.updateCustomField(a.alert, Seq((cfv, JsString("sad")))))

        r must beSuccessfulTry

        val updatedAlert = db.roTransaction(implicit graph => alertSrv.initSteps.get(a.alert._id).richAlert.getOrFail().get)

        updatedAlert.customFields must not(beEmpty)
        updatedAlert.customFields.find(_.name == "string1").get.value must beSome.which(v => v shouldEqual "sad")
      }

      "mark as read/unread an alert" in {
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 6", "test 6", "test desc 6", Set.empty)
          )
          .get

        a.alert.read must beFalse

        def getAlert = db.roTransaction(implicit graph => alertSrv.getOrFail(a._id).get)

        // Read it
        db.tryTransaction(implicit graph => alertSrv.markAsRead(a._id))
        getAlert.read must beTrue

        // Unread it
        db.tryTransaction(implicit graph => alertSrv.markAsUnread(a._id))
        getAlert.read must beFalse
      }

      "follow/unfollow an alert" in {
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 7", "test 7", "test desc 7", Set.empty)
          )
          .get

        a.alert.follow must beFalse

        def getAlert = db.roTransaction(implicit graph => alertSrv.getOrFail(a._id).get)

        // Follow it
        db.tryTransaction(implicit graph => alertSrv.followAlert(a._id))
        getAlert.follow must beTrue

        // Unfollow it
        db.tryTransaction(implicit graph => alertSrv.unfollowAlert(a._id))
        getAlert.follow must beFalse
      }

      "create a case" in {
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 8", "test 8", "test desc 8", Set.empty)
          )
          .get

        val r = db.tryTransaction(
          implicit graph =>
            alertSrv.createCase(
              a,
              None,
              orgaSrv.getOrFail("cert").get
            )
        )

        r must beSuccessfulTry
        r.get.`case`.title shouldEqual "[SPAM] test 8"
      }

      "merge a case" in {
        // Create an alert with observables
        val a = db
          .tryTransaction(
            implicit graph => createAlert("test 9", "test 9", "test desc 9", Set("tag11", "tag12"))
          )
          .get
        val obs = db
          .tryTransaction(
            implicit graph => {
              observableSrv.create(
                Observable(Some("obs domain"), 1, ioc = false, sighted = false),
                observableTypeSrv.get("domain").getOrFail().get,
                "example.com",
                Set[String](),
                Nil
              )
            }
          )
          .get
        db.tryTransaction { implicit graph =>
          alertSrv.addObservable(a.alert, obs)
        } must beSuccessfulTry

        // Test case #1 merge
        val r = db
          .tryTransaction(implicit graph => alertSrv.mergeInCase(a._id, "#1"))

        r must beSuccessfulTry.which(c => {
          val richCase = db.roTransaction(implicit graph => caseSrv.get(c).richCase.getOrFail().get)

          richCase.tags.map(_.predicate) must contain(exactly("tag11", "tag12"))
          richCase.description must contain(s"\n  \n#### Merged with alert #${a.alert.sourceRef} ${a.alert.title}\n\n${a.alert.description.trim}")
          db.roTransaction(implicit graph => {
            caseSrv.get(c).observables.toList must contain(obs.observable)
            caseSrv.get(c).alert.getOrFail() must beSuccessfulTry
          })
        })
      }
    }
  }
}
