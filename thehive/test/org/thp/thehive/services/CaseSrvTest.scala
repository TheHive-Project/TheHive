package org.thp.thehive.services

import java.util.Date

import scala.util.Success

import play.api.libs.json.Json
import play.api.test.PlaySpecification

import org.specs2.matcher.Matcher
import org.thp.scalligraph.CreateError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class CaseSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext
  "case service" should {

    "list all cases" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].initSteps.toList.map(_.number) must contain(allOf(1, 2, 3))
      }
    }

    "get a case without impact status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase = app[CaseSrv].get("#1").richCase.head()
        richCase must_== RichCase(
          richCase._id,
          authContext.userId,
          richCase._updatedBy,
          richCase._createdAt,
          richCase._updatedAt,
          number = 1,
          title = "case#1",
          description = "description of case #1",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          tags = richCase.tags,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.Open,
          summary = None,
          impactStatus = None,
          resolutionStatus = None,
          user = Some("certuser@thehive.local"),
          Nil,
          Set(
            Permissions.manageTask,
            Permissions.manageCase,
            Permissions.manageObservable,
            Permissions.manageAlert,
            Permissions.manageAction,
            Permissions.manageAnalyse,
            Permissions.manageShare
          )
        )
        richCase.tags.map(_.toString) must contain(exactly("testNamespace.testPredicate=\"t1\"", "testNamespace.testPredicate=\"t3\""))
      }
    }

    // FIXME doesn't work with SBT ?!
    "get a case with impact status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase = app[CaseSrv].get("#2").richCase.head()
        richCase must_== RichCase(
          richCase._id,
          authContext.userId,
          richCase._updatedBy,
          richCase._createdAt,
          richCase._updatedAt,
          number = 2,
          title = "case#2",
          description = "description of case #2",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          tags = richCase.tags,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.Open,
          summary = None,
          impactStatus = Some("NoImpact"),
          resolutionStatus = None,
          user = Some("certuser@thehive.local"),
          Nil,
          Set(
            Permissions.manageTask,
            Permissions.manageCase,
            Permissions.manageObservable,
            Permissions.manageAlert,
            Permissions.manageAction,
            Permissions.manageAnalyse,
            Permissions.manageShare
          )
        )
        richCase.tags.map(_.toString) must contain(exactly("testNamespace.testPredicate=\"t2\"", "testNamespace.testPredicate=\"t1\""))
        richCase._createdBy must_=== "system@thehive.local"
      }
    }

    "get a case with custom fields" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase = app[CaseSrv].get("#3").richCase(DummyUserSrv(userId = "socuser@thehive.local", organisation = "soc").authContext).head()
        richCase.number must_=== 3
        richCase.title must_=== "case#3"
        richCase.description must_=== "description of case #3"
        richCase.severity must_=== 2
        richCase.startDate must_=== new Date(1531667370000L)
        richCase.endDate must beNone
        //        richCase.tags must contain( // TODO
        //          exactly(Tag.fromString("testNamespace.testPredicate=\"t1\""), Tag.fromString("testNamespace.testPredicate=\"t2\""))
        //        )
        richCase.flag must_=== false
        richCase.tlp must_=== 2
        richCase.pap must_=== 2
        richCase.status must_=== CaseStatus.Open
        richCase.summary must beNone
        richCase.impactStatus must beNone
        richCase.user must beSome("socuser@thehive.local")
        CustomField("boolean1", "boolean1", "boolean custom field", CustomFieldType.boolean, mandatory = false, options = Nil)
        richCase.customFields.map(f => (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
          )
        )
      }
    }

    "merge two cases" in testApp { app =>
      pending
    //      app[Database].transaction { implicit graph =>
    //        Seq("#2", "#3").toTry(app[CaseSrv].getOrFail) must beSuccessfulTry.which { cases: Seq[Case with Entity] ⇒
    //          val mergedCase = app[CaseSrv].merge(cases)(graph, dummyUserSrv.getSystemAuthContext)
    //
    //          mergedCase.title must_=== "case#2 / case#3"
    //          mergedCase.description must_=== "description of case #2\n\ndescription of case #3"
    //          mergedCase.severity must_=== 2
    //          mergedCase.startDate must_=== new Date(1531667370000L)
    //          mergedCase.endDate must beNone
    //          mergedCase.tags must_=== Nil
    //          mergedCase.flag must_=== false
    //          mergedCase.tlp must_=== 2
    //          mergedCase.pap must_=== 2
    //          mergedCase.status must_=== CaseStatus.Open
    //          mergedCase.summary must beNone
    //          mergedCase.impactStatus must beNone
    //          mergedCase.user must beSome("test")
    //          mergedCase.customFields.map(f ⇒ (f.name, f.typeName, f.value)) must contain(
    //            allOf[(String, String, Option[Any])](
    //              ("boolean1", "boolean", Some(true)),
    //              ("string1", "string", Some("string1 custom field"))
    //            ))
    //        }
    //      }
    }

    "add custom field with wrong type" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail("#3") must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, "boolean1", Some("plop")) must beFailedTry
        }
      }
    }

    "add custom field" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail("#3") must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, "boolean1", Some(true)) must beSuccessfulTry
          app[CaseSrv].getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == true)
        }
      }
    }

    "update custom field" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail("#3") must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, "boolean1", Some(false)) must beSuccessfulTry
          app[CaseSrv].getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == false)
        }
      }
    }

    "update case title" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].get("#3").update("title" -> "new title")
        app[CaseSrv].getOrFail("#3") must beSuccessfulTry.which { `case`: Case with Entity =>
          `case`.title must_=== "new title"
        }
      }
    }

    "get correct next case number" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].nextCaseNumber shouldEqual 4
      }
    }

    "close a case properly" in testApp { app =>
      val updates = Seq(PropertyUpdater(FPathElem("status"), CaseStatus.Resolved) { (vertex, _, _, _) =>
        vertex.property("status", CaseStatus.Resolved)
        Success(Json.obj("status" -> CaseStatus.Resolved))
      })

      val r = app[Database].tryTransaction(implicit graph => app[CaseSrv].update(app[CaseSrv].get("#1"), updates))

      r must beSuccessfulTry

      val updatedCase = app[Database].roTransaction(implicit graph => app[CaseSrv].get("#1").getOrFail().get)
      updatedCase.status shouldEqual CaseStatus.Resolved
      updatedCase.endDate must beSome
    }

    "upsert case tags" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          c3 <- app[CaseSrv].get("#3").getOrFail()
          _  <- app[CaseSrv].updateTagNames(c3, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="yolo""""))
        } yield app[CaseSrv].get(c3).tags.toList.map(_.toString)
      } must beASuccessfulTry.which { tags =>
        tags must contain(exactly("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="yolo""""))
      }
    }

    "add new tags and not previous ones" in testApp { app =>
      // Create a case with tags first
      val c = app[Database]
        .tryTransaction(implicit graph =>
          app[CaseSrv].create(
            Case(0, "case 5", "desc 5", 1, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
            None,
            app[OrganisationSrv].getOrFail("cert").get,
            app[TagSrv].initSteps.toList.toSet,
            Map.empty,
            None,
            Nil
          )
        )
        .get

      c.tags must not(beEmpty)

      val currentLen = c.tags.length

      app[Database].tryTransaction(implicit graph =>
        app[CaseSrv].addTags(c.`case`, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""))
      ) must beSuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].initSteps.has("title", "case 5").tags.toList.length shouldEqual currentLen + 1
      }
    }

    "add an observable if not existing" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val c1          = app[CaseSrv].get("#1").getOrFail().get
        val observables = app[ObservableSrv].initSteps.richObservable.toList

        observables must not(beEmpty)

        val hfr = observables.find(_.message.contains("Some weird domain")).get

        app[Database].tryTransaction { implicit graph =>
          app[CaseSrv].addObservable(c1, hfr)
        }.get must throwA[CreateError]

        val newObs = app[Database].tryTransaction { implicit graph =>
          app[ObservableSrv].create(
            Observable(Some("if you feel lost"), 1, ioc = false, sighted = true),
            app[ObservableTypeSrv].get("domain").getOrFail().get,
            "lost.com",
            Set[String](),
            Nil
          )
        }.get

        app[Database].tryTransaction { implicit graph =>
          app[CaseSrv].addObservable(c1, newObs)
        } must beSuccessfulTry
      }
    }

    "remove a case and its dependencies" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val c1 = app[Database]
          .tryTransaction(implicit graph =>
            app[CaseSrv].create(
              Case(0, "case 9", "desc 9", 1, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
              None,
              app[OrganisationSrv].getOrFail("cert").get,
              Set[Tag with Entity](),
              Map.empty,
              None,
              Nil
            )
          )
          .get

        app[Database].tryTransaction(implicit graph => app[CaseSrv].cascadeRemove(c1.`case`)) must beSuccessfulTry
        app[Database].roTransaction { implicit graph =>
          app[CaseSrv].get(c1._id).exists() must beFalse
        }
      }
    }
    "set or unset case impact status" in testApp { app =>
      app[Database]
        .tryTransaction { implicit graph =>
          for {
            case0 <- app[CaseSrv].create(
              Case(0, "case 6", "desc 6", 1, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
              None,
              app[OrganisationSrv].getOrFail("cert").get,
              app[TagSrv].initSteps.toList.toSet,
              Map.empty,
              None,
              Nil
            )

            _ = app[CaseSrv].get(case0._id).impactStatus.exists() must beFalse
            _ <- app[CaseSrv].setImpactStatus(case0.`case`, "WithImpact")
            _ <- app[CaseSrv].get(case0._id).impactStatus.getOrFail()
            _ <- app[CaseSrv].unsetImpactStatus(case0.`case`)
            _ = app[CaseSrv].get(case0._id).impactStatus.exists() must beFalse
          } yield ()
        } must beASuccessfulTry
    }

    "set or unset case resolution status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val c7 = app[Database]
          .tryTransaction(implicit graph =>
            app[CaseSrv].create(
              Case(0, "case 7", "desc 7", 1, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
              None,
              app[OrganisationSrv].getOrFail("cert").get,
              app[TagSrv].initSteps.toList.toSet,
              Map.empty,
              None,
              Nil
            )
          )
          .get

        app[CaseSrv].get(c7._id).resolutionStatus.exists() must beFalse
        app[Database].tryTransaction(implicit graph => app[CaseSrv].setResolutionStatus(c7.`case`, "Duplicated")) must beSuccessfulTry
        app[Database].roTransaction(implicit graph => app[CaseSrv].get(c7._id).resolutionStatus.exists() must beTrue)
        app[Database].tryTransaction(implicit graph => app[CaseSrv].unsetResolutionStatus(c7.`case`)) must beSuccessfulTry
        app[Database].roTransaction(implicit graph => app[CaseSrv].get(c7._id).resolutionStatus.exists() must beFalse)
      }
    }

    "assign/unassign a case" in testApp { app =>
      val c8 = app[Database]
        .tryTransaction(implicit graph =>
          app[CaseSrv].create(
            Case(0, "case 8", "desc 8", 2, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
            Some(app[UserSrv].get("certuser@thehive.local").getOrFail().get),
            app[OrganisationSrv].getOrFail("cert").get,
            app[TagSrv].initSteps.toList.toSet,
            Map.empty,
            None,
            Nil
          )
        )
        .get
        .`case`

      def checkAssignee(status: Matcher[Boolean]) = app[Database].roTransaction(implicit graph => app[CaseSrv].get(c8).assignee.exists() must status)

      checkAssignee(beTrue)
      app[Database].tryTransaction(implicit graph => app[CaseSrv].unassign(c8)) must beSuccessfulTry
      checkAssignee(beFalse)
      app[Database].tryTransaction(implicit graph => app[CaseSrv].assign(c8, app[UserSrv].get("certuser@thehive.local").getOrFail().get)) must beSuccessfulTry
      checkAssignee(beTrue)
    }

    "show only visible cases" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].get("#3").visible.getOrFail() must beFailedTry
      }
    }

    "forbid correctly case access" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv]
          .get("#1")
          .can(Permissions.manageCase)(DummyUserSrv(userId = "certro@thehive.local", organisation = "cert").authContext)
          .exists() must beFalse
      }
    }

    "show linked cases" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].get("#1").linkedCases must beEmpty
        val observables = app[ObservableSrv].initSteps.richObservable.toList
        val hfr         = observables.find(_.message.contains("Some weird domain")).get

        app[Database].tryTransaction { implicit graph =>
          app[CaseSrv].addObservable(app[CaseSrv].get("#2").getOrFail().get, hfr)
        }

        app[Database].roTransaction(implicit graph => app[CaseSrv].get("#1").linkedCases must not(beEmpty))
      }
    }
  }
}
