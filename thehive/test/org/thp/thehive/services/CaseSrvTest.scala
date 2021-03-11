package org.thp.thehive.services

import org.specs2.matcher.Matcher
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadRequestError, EntityName}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ShareOps._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import java.util.Date
import scala.util.Success

class CaseSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Profile.analyst.permissions).authContext

  "case service" should {

    "list all cases" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].startTraversal.toSeq.map(_.number) must contain(allOf(1, 2, 3))
      }
    }

    "get a case without impact status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase = app[CaseSrv].get(EntityName("1")).richCase.head
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
          assignee = Some("certuser@thehive.local"),
          Nil,
          Set(
            Permissions.manageTask,
            Permissions.manageCase,
            Permissions.manageObservable,
            Permissions.manageAlert,
            Permissions.manageAction,
            Permissions.manageAnalyse,
            Permissions.manageShare,
            Permissions.managePage,
            Permissions.accessTheHiveFS
          ),
          richCase.`case`.organisationIds
        )
        richCase.tags must contain(exactly("t1", "t3"))
      }
    }

    "get a case with impact status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase = app[CaseSrv].get(EntityName("2")).richCase.head
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
          assignee = Some("certuser@thehive.local"),
          Nil,
          Set(
            Permissions.manageTask,
            Permissions.manageCase,
            Permissions.manageObservable,
            Permissions.manageAlert,
            Permissions.manageAction,
            Permissions.manageAnalyse,
            Permissions.manageShare,
            Permissions.managePage,
            Permissions.accessTheHiveFS
          ),
          richCase.`case`.organisationIds
        )
        richCase.tags must contain(exactly("t2", "t1"))
        richCase._createdBy must_=== "system@thehive.local"
      }
    }

    "get a case with custom fields" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val richCase =
          app[CaseSrv].get(EntityName("3")).richCase(DummyUserSrv(userId = "socuser@thehive.local", organisation = "soc").authContext).head
        richCase.number must_=== 3
        richCase.title must_=== "case#3"
        richCase.description must_=== "description of case #3"
        richCase.severity must_=== 2
        richCase.startDate must_=== new Date(1531667370000L)
        richCase.endDate must beNone
        richCase.tags must contain(exactly("t1", "t2"))
        richCase.flag must_=== false
        richCase.tlp must_=== 2
        richCase.pap must_=== 2
        richCase.status must_=== CaseStatus.Open
        richCase.summary must beNone
        richCase.impactStatus must beNone
        richCase.assignee must beSome("socuser@thehive.local")
        CustomField("boolean1", "boolean1", "boolean custom field", CustomFieldType.boolean, mandatory = false, options = Nil)
        richCase.customFields.map(f => (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
          )
        )
      }
    }

    "add custom field with wrong type" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, EntityName("boolean1"), Some("plop"), None) must beFailedTry
        }
      }
    }

    "add custom field" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, EntityName("boolean1"), Some(true), None) must beSuccessfulTry
          app[CaseSrv].getCustomField(`case`, EntityName("boolean1")).flatMap(_.value) must beSome.which(_ == true)
        }
      }
    }

    "update custom field" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          app[CaseSrv].setOrCreateCustomField(`case`, EntityName("boolean1"), Some(false), None) must beSuccessfulTry
          app[CaseSrv].getCustomField(`case`, EntityName("boolean1")).flatMap(_.value) must beSome.which(_ == false)
        }
      }
    }

    "update case title" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[CaseSrv].get(EntityName("3")).update(_.title, "new title").getOrFail("Case")
        app[CaseSrv].getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          `case`.title must_=== "new title"
        }
      }
    }

    "get correct next case number" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].nextCaseNumber shouldEqual 27
      }
    }

    "close a case properly" in testApp { app =>
      val updates = Seq(PropertyUpdater(FPathElem("status"), CaseStatus.Resolved) { (vertex, _, _) =>
        vertex.property("status", CaseStatus.Resolved)
        Success(Json.obj("status" -> CaseStatus.Resolved))
      })

      val r = app[Database].tryTransaction(implicit graph => app[CaseSrv].update(app[CaseSrv].get(EntityName("1")), updates))

      r must beSuccessfulTry

      val updatedCase = app[Database].roTransaction(implicit graph => app[CaseSrv].get(EntityName("1")).getOrFail("Case").get)
      updatedCase.status shouldEqual CaseStatus.Resolved
      updatedCase.endDate must beSome
    }

    "upsert case tags" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          c3 <- app[CaseSrv].get(EntityName("3")).getOrFail("Case")
          _  <- app[CaseSrv].updateTags(c3, Set("t2", "yolo"))
        } yield app[CaseSrv].get(c3).tags.toList.map(_.toString)
      } must beASuccessfulTry.which { tags =>
        tags must contain(exactly("t2", "yolo"))
      }
    }

    "add new tags and not previous ones" in testApp { app =>
      // Create a case with tags first
      val c = app[Database].tryTransaction { implicit graph =>
        val organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get
        app[CaseSrv].create(
          Case(
            title = "case 5",
            description = "desc 5",
            severity = 1,
            startDate = new Date(),
            endDate = None,
            flag = false,
            tlp = 2,
            pap = 3,
            status = CaseStatus.Open,
            summary = None,
            tags = Seq("tag1", "tag2")
          ),
          assignee = None,
          organisation,
          Seq.empty,
          None,
          Nil
        )
      }.get

      c.tags must not(beEmpty)

      val currentLen = c.tags.length

      app[Database].tryTransaction(implicit graph => app[CaseSrv].addTags(c.`case`, Set("tag1", "tag3"))) must beSuccessfulTry

      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].startTraversal.has(_.title, "case 5").tags.toList.length shouldEqual currentLen + 1
      }
    }

    "add an observable if not existing" in testApp { app =>
      //      app[Database].roTransaction { implicit graph =>
      //        val c1          = app[CaseSrv].get(EntityName("1")).getOrFail("Case").get
      //        val observables = app[ObservableSrv].startTraversal.richObservable.toList
      //
      //        observables must not(beEmpty)
      //
      //        val hfr = observables.find(_.message.contains("Some weird domain")).get
      //
      //        app[Database].tryTransaction { implicit graph =>
      ////          app[CaseSrv].addObservable(c1, hfr)
      //          app[CaseSrv].createObservable(c1, hfr, hfr.data.get)
      //        }.get must throwA[CreateError]
      //
      //        val newObs = app[Database].tryTransaction { implicit graph =>
      //          val organisation = app[OrganisationSrv].current.getOrFail("Organisation").get
      //          app[ObservableSrv].create(
      //            Observable(
      //              message = Some("if you feel lost"),
      //              tlp = 1,
      //              ioc = false,
      //              sighted = true,
      //              ignoreSimilarity = None,
      //              dataType = "domain",
      //              tags = Nil,
      //              organisationIds = Seq(organisation._id),
      //              relatedId = c1._id
      //            ),
      //            "lost.com"
      //          )
      //        }.get
      //
      //        app[Database].tryTransaction { implicit graph =>
      //          app[CaseSrv].addObservable(c1, newObs)
      //        } must beSuccessfulTry
      //      }
      pending
    }

    "remove a case and its dependencies" in testApp { app =>
      val c1 = app[Database].tryTransaction { implicit graph =>
        val organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get
        app[CaseSrv].create(
          Case(
            title = "case 9",
            description = "desc 9",
            severity = 1,
            startDate = new Date(),
            endDate = None,
            flag = false,
            tlp = 2,
            pap = 3,
            status = CaseStatus.Open,
            summary = None,
            tags = Nil
          ),
          assignee = None,
          organisation,
          Seq.empty,
          None,
          Nil
        )
      }.get

      app[Database].tryTransaction(implicit graph => app[CaseSrv].remove(c1.`case`)) must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].get(c1._id).exists must beFalse
      }
    }

    "set or unset case impact status" in testApp { app =>
      app[Database]
        .tryTransaction { implicit graph =>
          for {
            organisation <- app[OrganisationSrv].getOrFail(EntityName("cert"))
            case0 <- app[CaseSrv].create(
              Case(
                title = "case 6",
                description = "desc 6",
                severity = 1,
                startDate = new Date(),
                endDate = None,
                flag = false,
                tlp = 2,
                pap = 3,
                status = CaseStatus.Open,
                summary = None,
                tags = Seq("tag1", "tag2")
              ),
              assignee = None,
              organisation,
              Seq.empty,
              None,
              Nil
            )

            _ = app[CaseSrv].get(case0._id).impactStatus.exists must beFalse
            _ <- app[CaseSrv].setImpactStatus(case0.`case`, "WithImpact")
            _ <- app[CaseSrv].get(case0._id).impactStatus.getOrFail("Case")
            _ <- app[CaseSrv].unsetImpactStatus(case0.`case`)
            _ = app[CaseSrv].get(case0._id).impactStatus.exists must beFalse
          } yield ()
        } must beASuccessfulTry
    }

    "set or unset case resolution status" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        val c7 = app[Database].tryTransaction { implicit graph =>
          val organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get
          app[CaseSrv].create(
            Case(
              title = "case 7",
              description = "desc 7",
              severity = 1,
              startDate = new Date(),
              endDate = None,
              flag = false,
              tlp = 2,
              pap = 3,
              status = CaseStatus.Open,
              summary = None,
              tags = Seq("tag1", "tag2")
            ),
            assignee = None,
            organisation,
            Seq.empty,
            None,
            Nil
          )
        }.get

        app[CaseSrv].get(c7._id).resolutionStatus.exists must beFalse
        app[Database].tryTransaction(implicit graph => app[CaseSrv].setResolutionStatus(c7.`case`, "Duplicated")) must beSuccessfulTry
        app[Database].roTransaction(implicit graph => app[CaseSrv].get(c7._id).resolutionStatus.exists must beTrue)
        app[Database].tryTransaction(implicit graph => app[CaseSrv].unsetResolutionStatus(c7.`case`)) must beSuccessfulTry
        app[Database].roTransaction(implicit graph => app[CaseSrv].get(c7._id).resolutionStatus.exists must beFalse)
      }
    }

    "assign/unassign a case" in testApp { app =>
      val c8 = app[Database]
        .tryTransaction { implicit graph =>
          val organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get
          val certuser     = app[UserSrv].getOrFail(EntityName("certuser@thehive.local")).get
          app[CaseSrv].create(
            Case(
              title = "case 8",
              description = "desc 8",
              severity = 2,
              startDate = new Date(),
              endDate = None,
              flag = false,
              tlp = 2,
              pap = 3,
              status = CaseStatus.Open,
              summary = None,
              tags = Seq("tag1", "tag2"),
              assignee = Some("certuser@thehive.local")
            ),
            assignee = Some(certuser),
            organisation,
            Seq.empty,
            None,
            Nil
          )
        }
        .get
        .`case`

      def checkAssignee(status: Matcher[Boolean]) =
        app[Database].roTransaction(implicit graph => app[CaseSrv].get(c8).assignee.exists must status)

      checkAssignee(beTrue)
      app[Database].tryTransaction(implicit graph => app[CaseSrv].unassign(c8)) must beSuccessfulTry
      checkAssignee(beFalse)
      app[Database].tryTransaction(implicit graph =>
        app[CaseSrv].assign(c8, app[UserSrv].get(EntityName("certuser@thehive.local")).getOrFail("Case").get)
      ) must beSuccessfulTry
      checkAssignee(beTrue)
    }

    "show only visible cases" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv].get(EntityName("3")).visible(app[OrganisationSrv]).getOrFail("Case") must beFailedTry
      }
    }

    "forbid correctly case access" in testApp { app =>
      app[Database].roTransaction { implicit graph =>
        app[CaseSrv]
          .get(EntityName("1"))
          .can(Permissions.manageCase)(DummyUserSrv(userId = "certro@thehive.local", organisation = "cert").authContext)
          .exists must beFalse
      }
    }

    "show linked cases" in testApp { app =>
      //      app[Database].roTransaction { implicit graph =>
      //        app[CaseSrv].get(EntityName("1")).linkedCases must beEmpty
      //        val observables = app[ObservableSrv].startTraversal.richObservable.toList
      //        val hfr         = observables.find(_.message.contains("Some weird domain")).get
      //
      //        app[Database].tryTransaction { implicit graph =>
      //          app[CaseSrv].addObservable(app[CaseSrv].get(EntityName("2")).getOrFail("Case").get, hfr)
      //        }
      //
      //        app[Database].roTransaction(implicit graph => app[CaseSrv].get(EntityName("1")).linkedCases must not(beEmpty))
      //      }
      pending
    }

    "merge cases, happy path with one organisation" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        def case21 = app[CaseSrv].get(EntityName("21")).clone()

        def case22 = app[CaseSrv].get(EntityName("22")).clone()

        def case23 = app[CaseSrv].get(EntityName("23")).clone()
        // Procedures
        case21.procedure.toSeq.size mustEqual 1
        case22.procedure.toSeq.size mustEqual 2
        case23.procedure.toSeq.size mustEqual 0
        // CustomFields
        case21.customFields.toSeq.size mustEqual 0
        case22.customFields.toSeq.size mustEqual 1
        case23.customFields.toSeq.size mustEqual 1
        // Tasks
        case21.tasks.toSeq.size mustEqual 2
        case22.tasks.toSeq.size mustEqual 0
        case23.tasks.toSeq.size mustEqual 1
        // Observables
        case21.observables.toSeq.size mustEqual 1
        case22.observables.toSeq.size mustEqual 0
        case23.observables.toSeq.size mustEqual 2
        // Alerts
        case21.alert.toSeq.size mustEqual 1
        case22.alert.toSeq.size mustEqual 0
        case23.alert.toSeq.size mustEqual 0

        for {
          c21     <- case21.getOrFail("Case")
          c22     <- case22.getOrFail("Case")
          c23     <- case23.getOrFail("Case")
          newCase <- app[CaseSrv].merge(Seq(c21, c22, c23))
        } yield newCase
      } must beASuccessfulTry.which { richCase =>
        app[Database].roTransaction { implicit graph =>
          def mergedCase = app[CaseSrv].get(EntityName(richCase.number.toString)).clone()

          mergedCase.procedure.toSeq.size mustEqual 3
          mergedCase.customFields.toSeq.size mustEqual 2
          mergedCase.tasks.toSeq.size mustEqual 3
          mergedCase.observables.toSeq.size mustEqual 3
          mergedCase.alert.toSeq.size mustEqual 1

          app[CaseSrv].get(EntityName("21")).getOrFail("Case") must beAFailedTry
          app[CaseSrv].get(EntityName("22")).getOrFail("Case") must beAFailedTry
          app[CaseSrv].get(EntityName("23")).getOrFail("Case") must beAFailedTry
        }
      }
    }

    "refuse to merge cases with different shares" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        val case21 = app[CaseSrv].getOrFail(EntityName("21")).get
        val case24 = app[CaseSrv].getOrFail(EntityName("24")).get
        val case26 = app[CaseSrv].getOrFail(EntityName("26")).get
        app[CaseSrv].merge(Seq(case21, case24, case26))
      } must beFailedTry.withThrowable[BadRequestError]
    }

    "merge cases, happy path with three organisations" in testApp { app =>
      implicit val authContext: AuthContext =
        DummyUserSrv(organisation = "soc", permissions = Profile.analyst.permissions).authContext

      def getCase(number: Int)(implicit graph: Graph): Traversal.V[Case] = app[CaseSrv].getByName(number.toString)

      app[Database].tryTransaction { implicit graph =>
        // Tasks
        getCase(24).share(EntityName("cert")).tasks.getCount mustEqual 1
        getCase(24).share(EntityName("soc")).tasks.getCount mustEqual 2
        getCase(25).share(EntityName("cert")).tasks.getCount mustEqual 0
        getCase(25).share(EntityName("soc")).tasks.getCount mustEqual 0

        // Observables
        getCase(24).share(EntityName("cert")).observables.getCount mustEqual 0
        getCase(24).share(EntityName("soc")).observables.getCount mustEqual 0
        getCase(25).share(EntityName("cert")).observables.getCount mustEqual 2
        getCase(25).share(EntityName("soc")).observables.getCount mustEqual 1

        for {
          c24     <- getCase(24).getOrFail("Case")
          c25     <- getCase(25).getOrFail("Case")
          newCase <- app[CaseSrv].merge(Seq(c24, c25))
        } yield newCase
      } must beASuccessfulTry.which { richCase =>
        app[Database].roTransaction { implicit graph =>
          getCase(richCase.number).share(EntityName("cert")).tasks.getCount mustEqual 1
          getCase(richCase.number).share(EntityName("soc")).tasks.getCount mustEqual 2
          getCase(richCase.number).share(EntityName("cert")).observables.getCount mustEqual 2
          getCase(richCase.number).share(EntityName("soc")).observables.getCount mustEqual 1

          getCase(24).getOrFail("Case") must beAFailedTry
          getCase(25).getOrFail("Case") must beAFailedTry
        }
      }
    }
  }
}
