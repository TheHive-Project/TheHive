package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.{EntityId, EntityIdOrName, EntityName}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import play.api.libs.json.JsString
import play.api.test.PlaySpecification

import java.util.Date
import eu.timepit.refined.auto._

class AlertSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "alert service" should {
    "create an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      val a = database.tryTransaction { implicit graph =>
        val organisation = organisationSrv.getOrFail(EntityName("cert")).get
        alertSrv.create(
          Alert(
            `type` = "test",
            source = "#1",
            sourceRef = "alert_creation_test",
            externalLink = Some("test.com"),
            title = "test",
            description = "test desc",
            severity = 0,
            date = new Date(),
            lastSyncDate = new Date(),
            tlp = 1,
            pap = 2,
            read = false,
            follow = false,
            organisationId = organisation._id,
            tags = Seq("tag1", "tag2"),
            caseId = EntityId.empty
          ),
          organisation,
          Set("tag1", "tag2"),
          Seq(InputCustomFieldValue("string1", Some("lol"), None)),
          Some(caseTemplateSrv.getOrFail(EntityName("spam")).get)
        )
      }
      a must beSuccessfulTry.which { a =>
        a.title shouldEqual "test"
        a.source shouldEqual "#1"
        a.sourceRef shouldEqual "alert_creation_test"
        a.externalLink shouldEqual Some("test.com")
        a.description shouldEqual "test desc"
        a.severity shouldEqual 0
        a.tlp shouldEqual 1
        a.pap shouldEqual 2
      }

      database.roTransaction { implicit graph =>
        organisationSrv.get(EntityName("cert")).alerts.toList must contain(a.get.alert)

        val tags = tagSrv.startTraversal.toSeq.filter(t => t.predicate == "tag1" || t.predicate == "tag2")

        alertSrv.get(a.get.alert).tags.toSeq must containTheSameElementsAs(tags)
      }
    }

    "update tags" in testApp { app =>
      import app._
      import app.thehiveModule._

      val newTags = database.tryTransaction { implicit graph =>
        for {
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
          _     <- alertSrv.updateTags(alert, Set("tag3", "tag5"))
        } yield alertSrv.get(EntityName("testType;testSource;ref1")).tags.toSeq
      }

      newTags must beSuccessfulTry.which(t => t.map(_.toString) must contain(exactly("tag3", "tag5")))
    }

    "update tag names" in testApp { app =>
      import app._
      import app.thehiveModule._

      val tags = database.tryTransaction { implicit graph =>
        for {
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
          _     <- alertSrv.updateTags(alert, Set("tag3", "tag5"))
        } yield alertSrv.get(EntityName("testType;testSource;ref1")).tags.toSeq
      }
      tags must beSuccessfulTry.which(t => t.map(_.toString) must contain(exactly("tag3", "tag5")))
    }

    "add tags" in testApp { app =>
      import app._
      import app.thehiveModule._

      val tags = database.tryTransaction { implicit graph =>
        for {
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
          _     <- alertSrv.addTags(alert, Set("tag7"))
        } yield alertSrv.get(EntityName("testType;testSource;ref1")).tags.toSeq
      }

      tags must beSuccessfulTry.which(t => t.map(_.toString) must contain(exactly("alert", "test", "tag7")))
    }

//    "add an observable if not existing" in testApp { app =>
    //       TODO clarify the expectation
//      val anObservable = Observable(
//        message = Some("if you are lost"),
//        tlp = 1,
//        ioc = false,
//        sighted = true,
//        ignoreSimilarity = None,
//        dataType = "domain",
//        tags = Seq("tag10")
//      )
//      database.tryTransaction { implicit graph =>
//        for {
//          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref4"))
//          _     <- alertSrv.createObservable(alert, anObservable, "perdu.com")
//        } yield ()
//      } must beASuccessfulTry
//
//      database.tryTransaction { implicit graph =>
//        for {
//          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
//          _     <- alertSrv.createObservable(alert, anObservable, "perdu.com")
//        } yield ()
//      } must beASuccessfulTry
//
//      database.roTransaction { implicit graph =>
//        alertSrv
//          .get(EntityName("testType;testSource;ref1"))
//          .observables
//          .filterOnData("perdu.com")
//          .filterOnType("domain")
//          .tags
//          .toSeq
//          .map(_.toString)
//      } must contain("tag10")
//    }

    "update custom fields" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
          cfv   <- customFieldSrv.getOrFail(EntityName("string1"))
          _     <- alertSrv.updateCustomField(alert, Seq((cfv, JsString("sad"))))
        } yield ()
      } must beSuccessfulTry

      database.roTransaction { implicit graph =>
        alertSrv.get(EntityName("testType;testSource;ref1")).customFieldValue(EntityIdOrName("string1")).nameJsonValue.headOption
      } must beSome("string1" -> JsString("sad"))
    }

    "mark as read an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          _     <- alertSrv.markAsRead(EntityName("testType;testSource;ref1"))
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
        } yield alert.read
      } must beASuccessfulTry(true)
    }

    "mark as unread an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          _     <- alertSrv.markAsUnread(EntityName("testType;testSource;ref1"))
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
        } yield alert.read
      } must beASuccessfulTry(false)
    }

    "mark as follow an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          _     <- alertSrv.followAlert(EntityName("testType;testSource;ref1"))
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
        } yield alert.follow
      } must beASuccessfulTry(true)
    }

    "mark as unfollow an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          _     <- alertSrv.unfollowAlert(EntityName("testType;testSource;ref1"))
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref1"))
        } yield alert.follow
      } must beASuccessfulTry(false)
    }

    "create a case" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          alert        <- alertSrv.get(EntityName("testType;testSource;ref1")).richAlert.getOrFail("Alert")
          organisation <- organisationSrv.getOrFail(EntityName("cert"))
          c            <- alertSrv.createCase(alert, None, organisation)
          _ = c.title must beEqualTo("[SPAM] alert#1")
          _ <- caseSrv.startTraversal.has(_.title, "[SPAM] alert#1").getOrFail("Alert")
        } yield ()
      } must beASuccessfulTry(())
    }

    "merge into an existing case" in testApp { app =>
      import app._
      import app.thehiveModule._

      database
        .tryTransaction { implicit graph =>
          alertSrv.mergeInCase(EntityName("testType;testSource;ref1"), EntityName("1"))
        } must beASuccessfulTry

      database.roTransaction { implicit graph =>
        val observables = caseSrv.get(EntityName("1")).observables.richObservable.toList
        observables must have size 1
        observables must contain { (o: RichObservable) =>
          o.data must beSome("h.fr")
          o.tags must contain("testDomain")
        }
      }
    }

    "remove totally an alert" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          alert <- alertSrv.getOrFail(EntityName("testType;testSource;ref4"))
          _     <- alertSrv.remove(alert)
        } yield ()
      } must beSuccessfulTry
      database.roTransaction { implicit graph =>
//        observableSrv.initSteps.filterOnType("domain").filterOnData("perdu.com").exists must beFalse
        alertSrv.startTraversal.get(EntityName("testType;testSource;ref4")).exists must beFalse
      }
    }
  }
}
