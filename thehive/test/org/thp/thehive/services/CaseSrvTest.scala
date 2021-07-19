package org.thp.thehive.services

import org.specs2.matcher.Matcher
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityName}
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import java.util.Date
import scala.util.Success

class CaseSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Profile.analyst.permissions).authContext

  "case service" should {

    "list all cases" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        caseSrv.startTraversal.toSeq.map(_.number) must contain(allOf(1, 2, 3))
      }
    }

    "get a case without impact status" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        val richCase = caseSrv.get(EntityName("1")).richCase.head
        val expected = RichCase(
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
            Permissions.manageProcedure,
            Permissions.accessTheHiveFS,
            Permissions.manageProcedure
          ),
          richCase.`case`.organisationIds,
          None,
          richCase.`case`.owningOrganisation
        )
        richCase must_== expected
        richCase.tags must contain(exactly("t1", "t3"))
      }
    }

    "get a case with impact status" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        val richCase = caseSrv.get(EntityName("2")).richCase.head
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
            Permissions.accessTheHiveFS,
            Permissions.manageProcedure
          ),
          richCase.`case`.organisationIds,
          None,
          richCase.`case`.owningOrganisation
        )
        richCase.tags must contain(exactly("t2", "t1"))
        richCase._createdBy must_=== "system@thehive.local"
      }
    }

    "get a case with custom fields" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        val richCase =
          caseSrv.get(EntityName("3")).richCase(DummyUserSrv(userId = "socuser@thehive.local", organisation = "soc").authContext).head
        richCase.number must_=== 3
        richCase.title must_=== "case#3"
        richCase.description must_=== "description of case #3"
        richCase.severity must_=== 2
        richCase.startDate must_=== new Date(1531667370000L)
        richCase.endDate must beNone
        richCase.tags    must contain(exactly("t1", "t2"))
        richCase.flag must_=== false
        richCase.tlp must_=== 2
        richCase.pap must_=== 2
        richCase.status must_=== CaseStatus.Open
        richCase.summary      must beNone
        richCase.impactStatus must beNone
        richCase.assignee     must beSome("socuser@thehive.local")
        CustomField("boolean1", "boolean1", "boolean custom field", CustomFieldBoolean, mandatory = false, options = Nil)
        richCase.customFields.map(f => (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
          )
        )
      }
    }

    "add custom field with wrong type" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        caseSrv.getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, EntityName("boolean1"), Some("plop"), None) must beFailedTry
        }
      }
    }

    "add custom field" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        caseSrv.getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, EntityName("boolean1"), Some(true), None) must beSuccessfulTry
          caseSrv.getCustomField(`case`, EntityName("boolean1")).flatMap(_.value)          must beSome.which(_ == true)
        }
      }
    }

    "update custom field" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        caseSrv.getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, EntityName("boolean1"), Some(false), None) must beSuccessfulTry
          caseSrv.getCustomField(`case`, EntityName("boolean1")).flatMap(_.value)           must beSome.which(_ == false)
        }
      }
    }

    "update case title" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        caseSrv.get(EntityName("3")).update(_.title, "new title").getOrFail("Case")
        caseSrv.getOrFail(EntityName("3")) must beSuccessfulTry.which { `case`: Case with Entity =>
          `case`.title must_=== "new title"
        }
      }
    }

    "get correct next case number" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        caseSrv.nextCaseNumber shouldEqual 37
      }
    }

    "close a case properly" in testApp { app =>
      import app._
      import app.thehiveModule._

      val updates = Seq(PropertyUpdater(FPathElem("status"), CaseStatus.Resolved) { (vertex, _, _) =>
        vertex.property("status", CaseStatus.Resolved)
        Success(Json.obj("status" -> CaseStatus.Resolved))
      })

      val r = database.tryTransaction(implicit graph => caseSrv.update(caseSrv.get(EntityName("1")), updates))

      r must beSuccessfulTry

      val updatedCase = database.roTransaction(implicit graph => caseSrv.get(EntityName("1")).getOrFail("Case").get)
      updatedCase.status shouldEqual CaseStatus.Resolved
      updatedCase.endDate must beSome
    }

    "upsert case tags" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          c3 <- caseSrv.get(EntityName("3")).getOrFail("Case")
          _  <- caseSrv.updateTags(c3, Set("t2", "yolo"))
        } yield caseSrv.get(c3).tags.toList.map(_.toString)
      } must beASuccessfulTry.which { tags =>
        tags must contain(exactly("t2", "yolo"))
      }
    }

    "add new tags and not previous ones" in testApp { app =>
      import app._
      import app.thehiveModule._

      // Create a case with tags first
      val c = database.tryTransaction { implicit graph =>
        val organisation = organisationSrv.getOrFail(EntityName("cert")).get
        caseSrv.create(
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
          Nil,
          Map.empty,
          None,
          None
        )
      }.get

      c.tags must not(beEmpty)

      val currentLen = c.tags.length

      database.tryTransaction(implicit graph => caseSrv.addTags(c.`case`, Set("tag1", "tag3"))) must beSuccessfulTry

      database.roTransaction { implicit graph =>
        caseSrv.startTraversal.has(_.title, "case 5").tags.toList.length shouldEqual currentLen + 1
      }
    }

    "add an observable if not existing" in testApp { app =>
//      import app._
//      import app.thehiveModule._

      //      database.roTransaction { implicit graph =>
      //        val c1          = caseSrv.get(EntityName("1")).getOrFail("Case").get
      //        val observables = observableSrv.startTraversal.richObservable.toList
      //
      //        observables must not(beEmpty)
      //
      //        val hfr = observables.find(_.message.contains("Some weird domain")).get
      //
      //        database.tryTransaction { implicit graph =>
      ////          caseSrv.addObservable(c1, hfr)
      //          caseSrv.createObservable(c1, hfr, hfr.data.get)
      //        }.get must throwA[CreateError]
      //
      //        val newObs = database.tryTransaction { implicit graph =>
      //          val organisation = organisationSrv.current.getOrFail("Organisation").get
      //          observableSrv.create(
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
      //        database.tryTransaction { implicit graph =>
      //          caseSrv.addObservable(c1, newObs)
      //        } must beSuccessfulTry
      //      }
      pending
    }

    "remove a case and its dependencies" in testApp { app =>
      import app._
      import app.thehiveModule._

      val c1 = database.tryTransaction { implicit graph =>
        val organisation = organisationSrv.getOrFail(EntityName("cert")).get
        caseSrv.create(
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
          Nil,
          Map.empty,
          None,
          None
        )
      }.get

      database.tryTransaction(implicit graph => caseSrv.delete(c1.`case`)) must beSuccessfulTry
      database.roTransaction { implicit graph =>
        caseSrv.get(c1._id).exists must beFalse
      }
    }

    "set or unset case impact status" in testApp { app =>
      import app._
      import app.thehiveModule._

      database
        .tryTransaction { implicit graph =>
          for {
            organisation <- organisationSrv.getOrFail(EntityName("cert"))
            case0 <- caseSrv.create(
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
              Nil,
              Map.empty,
              None,
              None
            )

            _ = caseSrv.get(case0._id).impactStatus.exists must beFalse
            _ <- caseSrv.setImpactStatus(case0.`case`, "WithImpact")
            _ <- caseSrv.get(case0._id).impactStatus.getOrFail("Case")
            _ <- caseSrv.unsetImpactStatus(case0.`case`)
            _ = caseSrv.get(case0._id).impactStatus.exists must beFalse
          } yield ()
        } must beASuccessfulTry
    }

    "set or unset case resolution status" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        val c7 = database.tryTransaction { implicit graph =>
          val organisation = organisationSrv.getOrFail(EntityName("cert")).get
          caseSrv.create(
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
            Nil,
            Map.empty,
            None,
            None
          )
        }.get

        caseSrv.get(c7._id).resolutionStatus.exists                                                     must beFalse
        database.tryTransaction(implicit graph => caseSrv.setResolutionStatus(c7.`case`, "Duplicated")) must beSuccessfulTry
        database.roTransaction(implicit graph => caseSrv.get(c7._id).resolutionStatus.exists must beTrue)
        database.tryTransaction(implicit graph => caseSrv.unsetResolutionStatus(c7.`case`)) must beSuccessfulTry
        database.roTransaction(implicit graph => caseSrv.get(c7._id).resolutionStatus.exists must beFalse)
      }
    }

    "assign/unassign a case" in testApp { app =>
      import app._
      import app.thehiveModule._

      val c8 = database
        .tryTransaction { implicit graph =>
          val organisation = organisationSrv.getOrFail(EntityName("cert")).get
          val certuser     = userSrv.getOrFail(EntityName("certuser@thehive.local")).get
          caseSrv.create(
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
            Nil,
            Map.empty,
            None,
            None
          )
        }
        .get
        .`case`

      def checkAssignee(status: Matcher[Boolean]) =
        database.roTransaction(implicit graph => caseSrv.get(c8).assignee.exists must status)

      checkAssignee(beTrue)
      database.tryTransaction(implicit graph => caseSrv.unassign(c8)) must beSuccessfulTry
      checkAssignee(beFalse)
      database.tryTransaction(implicit graph =>
        caseSrv.assign(c8, userSrv.get(EntityName("certuser@thehive.local")).getOrFail("Case").get)
      ) must beSuccessfulTry
      checkAssignee(beTrue)
    }

    "show only visible cases" in testApp { app =>
      import app._
      import app.thehiveModule._

      TheHiveOps(organisationSrv, customFieldSrv) { ops =>
        import ops.CaseOpsDefs
        database.roTransaction { implicit graph =>
          caseSrv
            .get(EntityName("3"))
            .visible
            .getOrFail("Case") must beFailedTry
        }
      }
    }

    "forbid correctly case access" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.roTransaction { implicit graph =>
        caseSrv
          .get(EntityName("1"))
          .can(Permissions.manageCase)(DummyUserSrv(userId = "certro@thehive.local", organisation = "cert").authContext)
          .exists must beFalse
      }
    }

    "show linked cases" in testApp { app =>
      //      database.roTransaction { implicit graph =>
      //        caseSrv.get(EntityName("1")).linkedCases must beEmpty
      //        val observables = observableSrv.startTraversal.richObservable.toList
      //        val hfr         = observables.find(_.message.contains("Some weird domain")).get
      //
      //        database.tryTransaction { implicit graph =>
      //          caseSrv.addObservable(caseSrv.get(EntityName("2")).getOrFail("Case").get, hfr)
      //        }
      //
      //        database.roTransaction(implicit graph => caseSrv.get(EntityName("1")).linkedCases must not(beEmpty))
      //      }
      pending
    }

    "merge cases, happy path with one organisation" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        def case21 = caseSrv.get(EntityName("21")).clone()

        def case22 = caseSrv.get(EntityName("22")).clone()

        def case23 = caseSrv.get(EntityName("23")).clone()
        // Procedures
        case21.procedure.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of procedure in case 21")
        case22.procedure.getCount must beEqualTo(2).updateMessage(s => s"$s: invalid number of procedure in case 22")
        case23.procedure.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of procedure in case 23")
        // CustomFields
        case21.customFields.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of custom fields in case 21")
        case22.customFields.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of custom fields in case 22")
        case23.customFields.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of custom fields in case 23")
        // Tasks
        case21.tasks.getCount must beEqualTo(2).updateMessage(s => s"$s: invalid number of tasks in case 21")
        case22.tasks.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of tasks in case 22")
        case23.tasks.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of tasks in case 23")
        // Observables
        case21.observables.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of observables in case 21")
        case22.observables.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of observables in case 22")
        case23.observables.getCount must beEqualTo(2).updateMessage(s => s"$s: invalid number of observables in case 23")
        // Alerts
        case21.alert.getCount must beEqualTo(1).updateMessage(s => s"$s: invalid number of alert in case 21")
        case22.alert.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of alert in case 22")
        case23.alert.getCount must beEqualTo(0).updateMessage(s => s"$s: invalid number of alert in case 23")

        for {
          c21     <- case21.getOrFail("Case")
          c22     <- case22.getOrFail("Case")
          c23     <- case23.getOrFail("Case")
          newCase <- caseSrv.merge(Seq(c21, c22, c23))
        } yield newCase
      } must beASuccessfulTry.which { richCase =>
        database.roTransaction { implicit graph =>
          def mergedCase = caseSrv.get(EntityName(richCase.number.toString)).clone()

          mergedCase.procedure.getCount    must beEqualTo(3).updateMessage(s => s"$s: invalid number of procedure in merged case")
          mergedCase.customFields.getCount must beEqualTo(2).updateMessage(s => s"$s: invalid number of customFields in merged case")
          mergedCase.tasks.getCount        must beEqualTo(3).updateMessage(s => s"$s: invalid number of tasks in merged case")
          mergedCase.observables.getCount  must beEqualTo(3).updateMessage(s => s"$s: invalid number of observables in merged case")
          mergedCase.alert.getCount        must beEqualTo(1).updateMessage(s => s"$s: invalid number of alert in merged case")

          caseSrv.get(EntityName("21")).getOrFail("Case") must beAFailedTry.updateMessage(s => s"$s: case 21 is not removed")
          caseSrv.get(EntityName("22")).getOrFail("Case") must beAFailedTry.updateMessage(s => s"$s: case 22 is not removed")
          caseSrv.get(EntityName("23")).getOrFail("Case") must beAFailedTry.updateMessage(s => s"$s: case 23 is not removed")
        }
      }
    }

    "refuse to merge cases with different shares" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        val case21 = caseSrv.getOrFail(EntityName("21")).get
        val case24 = caseSrv.getOrFail(EntityName("24")).get
        val case26 = caseSrv.getOrFail(EntityName("26")).get
        caseSrv.merge(Seq(case21, case24, case26))
      } must beFailedTry.withThrowable[BadRequestError]
    }

    "merge cases, happy path with three organisations" in testApp { app =>
      import app._
      import app.thehiveModule._

      implicit val authContext: AuthContext =
        DummyUserSrv(organisation = "soc", permissions = Profile.analyst.permissions).authContext

      def getCase(number: Int)(implicit graph: Graph): Traversal.V[Case] = caseSrv.getByName(number.toString)

      database.tryTransaction { implicit graph =>
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
          newCase <- caseSrv.merge(Seq(c24, c25))
        } yield newCase
      } must beASuccessfulTry.which { richCase =>
        database.roTransaction { implicit graph =>
          getCase(richCase.number).share(EntityName("cert")).tasks.getCount mustEqual 1
          getCase(richCase.number).share(EntityName("soc")).tasks.getCount mustEqual 2
          getCase(richCase.number).share(EntityName("cert")).observables.getCount mustEqual 2
          getCase(richCase.number).share(EntityName("soc")).observables.getCount mustEqual 1

          getCase(24).getOrFail("Case") must beAFailedTry
          getCase(25).getOrFail("Case") must beAFailedTry
        }
      }
    }

    "cascade remove, case not shared" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert", permissions = Set(Permissions.manageCase)).authContext

        def caze = caseSrv.startTraversal.has(_.number, 35).getOrFail("Case")
        caze must beSuccessfulTry

        def taskTraversal = taskSrv.startTraversal.has(_.title, "task-cascade-remove-simple")
        def taskDelete    = taskTraversal.getOrFail("Task")
        def logs          = taskTraversal.logs.toSeq.size
        def logsAttach    = taskTraversal.logs.attachments.toSeq.size
        taskDelete must beSuccessfulTry
        logs       must beEqualTo(1)
        logsAttach must beEqualTo(1)

        def obsTraversal = observableSrv.startTraversal.has(_.message, "obs-cascade-remove-simple")
        def obsDelete    = obsTraversal.getOrFail("Observable")
        def obsAttach    = obsTraversal.attachments.toSeq.size
        obsDelete must beSuccessfulTry
        obsAttach must beEqualTo(1)

        caseSrv.delete(caze.get) must beASuccessfulTry

        taskDelete must beAFailedTry
        logs       must beEqualTo(0)
        logsAttach must beEqualTo(0)
        obsDelete  must beAFailedTry
        obsAttach  must beEqualTo(0)
        caze       must beAFailedTry
      }
    }

    "cascade remove, shared" in testApp { app =>
//      import app._
//      import app.thehiveModule._
//
//      database.roTransaction { implicit graph =>
//        // Check users of soc have access to case 4
//        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc", permissions = Set(Permissions.manageCase)).authContext
//
//        caseSrv
//          .startTraversal
//          .has(_.number, 34)
//          .getOrFail("Case") must beSuccessfulTry
//
//        taskSrv
//          .startTraversal
//          .has(_.title, "task-cascade-remove-unshare")
//          .getOrFail("Task") must beSuccessfulTry
//
//        observableSrv
//          .startTraversal
//          .has(_.message, "obs-cascade-remove-unshare")
//          .getOrFail("Observable") must beSuccessfulTry
//      }
//
//      database.transaction { implicit graph =>
//        // Check entities & cascade remove the case
//        implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert", permissions = Set(Permissions.manageCase)).authContext
//
//        def caze = caseSrv.startTraversal.has(_.number, 34).getOrFail("Case")
//        caze must beSuccessfulTry
//
//        def taskTraversal  = taskSrv.startTraversal.has(_.title, "task-cascade-remove-delete")
//        def taskTraversal2 = taskSrv.startTraversal.has(_.title, "task-cascade-remove-unshare")
//        def taskDelete     = taskTraversal.getOrFail("Task")
//        def taskUnshare    = taskTraversal2.getOrFail("Task")
//        def logs           = taskTraversal.logs.toSeq.size
//        def logs2          = taskTraversal2.logs.toSeq.size
//        def taskAttach     = taskTraversal.logs.attachments.toSeq.size
//        def taskAttach2    = taskTraversal2.logs.attachments.toSeq.size
//        taskDelete  must beSuccessfulTry
//        logs        must beEqualTo(1)
//        taskAttach  must beEqualTo(1)
//        taskUnshare must beSuccessfulTry
//        logs2       must beEqualTo(0)
//        taskAttach2 must beEqualTo(0)
//
//        def obsTraversal  = observableSrv.startTraversal.has(_.message, "obs-cascade-remove-delete")
//        def obsTraversal2 = observableSrv.startTraversal.has(_.message, "obs-cascade-remove-unshare")
//        def obsDelete     = obsTraversal.getOrFail("Observable")
//        def obsUnshare    = obsTraversal2.getOrFail("Observable")
//        def obsAttach     = obsTraversal.attachments.toSeq.size
//        def obsAttach2    = obsTraversal2.attachments.toSeq.size
//        obsDelete  must beSuccessfulTry
//        obsAttach  must beEqualTo(1)
//        obsUnshare must beSuccessfulTry
//        obsAttach2 must beEqualTo(0)
//
//        caseSrv.delete(caze.get) must beASuccessfulTry
//
//        caze        must beASuccessfulTry
//        taskDelete  must beAFailedTry
//        taskUnshare must beASuccessfulTry
//        logs        must beEqualTo(0)
//        taskAttach  must beEqualTo(0)
//        logs2       must beEqualTo(0)
//        taskAttach2 must beEqualTo(0)
//        obsDelete   must beAFailedTry
//        obsUnshare  must beASuccessfulTry
//        obsAttach   must beEqualTo(0)
//        obsAttach2  must beEqualTo(0)
//      }
//
//      database.roTransaction { implicit graph =>
//        // Users of soc should still have access to case
//        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc", permissions = Set(Permissions.manageCase)).authContext
//
//        caseSrv
//          .startTraversal
//          .has(_.number, 4)
//          .getOrFail("Case") must beSuccessfulTry
//
//        taskSrv
//          .startTraversal
//          .has(_.title, "task-cascade-remove-unshare")
//          .getOrFail("Task") must beSuccessfulTry
//
//        observableSrv
//          .startTraversal
//          .has(_.message, "obs-cascade-remove-unshare")
//          .getOrFail("Observable") must beSuccessfulTry
//      }
      pending
    }.pendingUntilFixed

  }
}
