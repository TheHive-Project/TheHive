package org.thp.thehive.services

import java.util.Date

import org.thp.scalligraph.CreateError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import play.api.libs.json.JsString
import play.api.test.PlaySpecification

class AlertSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "alert service" should {
    "create an alert" in testApp { app =>
      val a = app[Database].tryTransaction { implicit graph =>
        app[AlertSrv].create(
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
            follow = false
          ),
          app[OrganisationSrv].getOrFail("cert").get,
          Set("tag1", "tag2"),
          Map("string1" -> Some("lol")),
          Some(app[CaseTemplateSrv].getOrFail("spam").get)
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

      app[Database].roTransaction { implicit graph =>
        app[OrganisationSrv].get("cert").alerts.toList must contain(a.get.alert)

        val tags = app[TagSrv].startTraversal.toSeq.filter(t => t.predicate == "tag1" || t.predicate == "tag2")

        app[AlertSrv].get(a.get.alert).tags.toSeq must containTheSameElementsAs(tags)
      }
    }

    "update tags" in testApp { app =>
      val newTags = app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
          tag3  <- app[TagSrv].getOrCreate("tag3")
          tag5  <- app[TagSrv].getOrCreate("tag5")
          _     <- app[AlertSrv].updateTags(alert, Set(tag3, tag5))
        } yield app[AlertSrv].get("testType;testSource;ref1").tags.toSeq
      }
      newTags must beSuccessfulTry.which(t => t.map(_.toString) must contain(exactly("tag3", "tag5")))
    }

    "update tag names" in testApp { app =>
      val tags = app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
          _     <- app[AlertSrv].updateTagNames(alert, Set("tag3", "tag5"))
        } yield app[AlertSrv].get("testType;testSource;ref1").tags.toSeq
      }
      tags must beSuccessfulTry.which(t => t.map(_.toString) must contain(exactly("tag3", "tag5")))
    }

    "add tags" in testApp { app =>
      val tags = app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
          _     <- app[AlertSrv].addTags(alert, Set("tag7"))
        } yield app[AlertSrv].get("testType;testSource;ref1").tags.toSeq
      }

      tags must beSuccessfulTry.which(t =>
        t.map(_.toString) must contain(exactly("testNamespace:testPredicate=\"alert\"", "testNamespace:testPredicate=\"test\"", "tag7"))
      )
    }

    "add an observable if not existing" in testApp { app =>
      val similarObs = app[Database].tryTransaction { implicit graph =>
        for {
          observableType <- app[ObservableTypeSrv].getOrFail("domain")
          observable <- app[ObservableSrv].create(
            observable = Observable(Some("if you are lost"), 1, ioc = false, sighted = true),
            `type` = observableType,
            dataValue = "perdu.com",
            tagNames = Set("tag10"),
            extensions = Nil
          )
        } yield observable
      }.get

      app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref4")
          _     <- app[AlertSrv].addObservable(alert, similarObs)
        } yield ()
      } must beAFailedTry.withThrowable[CreateError]

      app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
          _     <- app[AlertSrv].addObservable(alert, similarObs)
        } yield ()
      } must beASuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[AlertSrv].get("testType;testSource;ref1").observables.filterOnData("perdu.com").filterOnType("domain").exists
      } must beTrue
    }

    "update custom fields" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
          cfv   <- app[CustomFieldSrv].getOrFail("string1")
          _     <- app[AlertSrv].updateCustomField(alert, Seq((cfv, JsString("sad"))))
        } yield ()
      } must beSuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[AlertSrv].get("testType;testSource;ref1").customFields("string1").nameJsonValue.headOption
      } must beSome("string1" -> JsString("sad"))
    }

    "mark as read an alert" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          _     <- app[AlertSrv].markAsRead("testType;testSource;ref1")
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
        } yield alert.read
      } must beASuccessfulTry(true)
    }

    "mark as unread an alert" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          _     <- app[AlertSrv].markAsUnread("testType;testSource;ref1")
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
        } yield alert.read
      } must beASuccessfulTry(false)
    }

    "mark as follow an alert" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          _     <- app[AlertSrv].followAlert("testType;testSource;ref1")
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
        } yield alert.follow
      } must beASuccessfulTry(true)
    }

    "mark as unfollow an alert" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          _     <- app[AlertSrv].unfollowAlert("testType;testSource;ref1")
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref1")
        } yield alert.follow
      } must beASuccessfulTry(false)
    }

    "create a case" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          alert        <- app[AlertSrv].get("testType;testSource;ref1").richAlert.getOrFail("Alert")
          organisation <- app[OrganisationSrv].getOrFail("cert")
          c            <- app[AlertSrv].createCase(alert, None, organisation)
          _ = c.title must beEqualTo("[SPAM] alert#1")
          _ <- app[CaseSrv].startTraversal.has("title", "[SPAM] alert#1").getOrFail("Alert")
        } yield ()
      } must beASuccessfulTry(())
    }

    "merge into an existing case" in testApp { app =>
      app[Database]
        .tryTransaction { implicit graph =>
          app[AlertSrv].mergeInCase("testType;testSource;ref1", "#1")
        } must beASuccessfulTry

      app[Database].roTransaction { implicit graph =>
        val observables = app[CaseSrv].get("#1").observables.richObservable.toList
        observables must have size 1
        observables must contain { (o: RichObservable) =>
          o.data must beSome.which((_: Data).data must beEqualTo("h.fr"))
          o.tags.map(_.toString) must contain("testNamespace:testPredicate=\"testDomain\"", "testNamespace:testPredicate=\"hello\"").exactly
        }
      }
    }

    "remove totally an alert" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          alert <- app[AlertSrv].getOrFail("testType;testSource;ref4")
          _     <- app[AlertSrv].remove(alert)
        } yield ()
      } must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
//        app[ObservableSrv].initSteps.filterOnType("domain").filterOnData("perdu.com").exists must beFalse
        app[AlertSrv].startTraversal.get("testType;testSource;ref4").exists must beFalse
      }
    }
  }
}
