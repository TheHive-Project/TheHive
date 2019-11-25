package org.thp.thehive.services

import java.util.Date

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AppBuilder, CreateError}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import scala.util.{Success, Try}

class CaseSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseSrv: CaseSrv                     = app.instanceOf[CaseSrv]
    val orgaSrv: OrganisationSrv             = app.instanceOf[OrganisationSrv]
    val tagSrv: TagSrv                       = app.instanceOf[TagSrv]
    val taskSrv: TaskSrv                     = app.instanceOf[TaskSrv]
    val observableSrv: ObservableSrv         = app.instanceOf[ObservableSrv]
    val observableTypeSrv: ObservableTypeSrv = app.instanceOf[ObservableTypeSrv]
    val db: Database                         = app.instanceOf[Database]
    implicit val authContext: AuthContext    = dummyUserSrv.getSystemAuthContext

    s"[$name] case service" should {

      "list all cases" in db.roTransaction { implicit graph =>
        caseSrv.initSteps.toList.map(_.number) must contain(allOf(1, 2, 3, 4))
      }

      "get a case without impact status" in db.roTransaction { implicit graph =>
        val richCase = caseSrv.get("#1").richCase.head()
        richCase must_== RichCase(
          richCase._id,
          dummyUserSrv.userId,
          richCase._updatedBy,
          richCase._createdAt,
          richCase._updatedAt,
          number = 1,
          title = "case#1",
          description = "description of case #1",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          tags = Seq.empty,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.Open,
          summary = None,
          impactStatus = None,
          resolutionStatus = None,
          user = Some("user1@thehive.local"),
          Nil,
          Set(
            Permission("manageTask"),
            Permission("manageCase"),
            Permission("manageObservable"),
            Permission("manageAlert"),
            Permission("manageAction"),
            Permission("manageAnalyse")
          )
        )
      }

      "get a case with impact status" in db.roTransaction { implicit graph =>
        val richCase = caseSrv.get("#2").richCase.head()
        richCase must_== RichCase(
          richCase._id,
          dummyUserSrv.userId,
          richCase._updatedBy,
          richCase._createdAt,
          richCase._updatedAt,
          number = 2,
          title = "case#2",
          description = "description of case #2",
          severity = 2,
          startDate = new Date(1531667370000L),
          endDate = None,
          tags = Seq.empty,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.Open,
          summary = None,
          impactStatus = Some("NoImpact"),
          resolutionStatus = None,
          user = Some("user2@thehive.local"),
          Nil,
          Set(
            Permission("manageTask"),
            Permission("manageCase"),
            Permission("manageObservable"),
            Permission("manageAlert"),
            Permission("manageAction"),
            Permission("manageAnalyse")
          )
        )
        richCase._createdBy must_=== dummyUserSrv.userId
      }

      "get a case with custom fields" in db.roTransaction { implicit graph =>
        val richCase = caseSrv.get("#3").richCase.head()
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
        richCase.user must beSome("user1@thehive.local")
        CustomField("boolean1", "boolean1", "boolean custom field", CustomFieldType.boolean, mandatory = false, options = Nil)
        richCase.customFields.map(f => (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "stobservableSrvring", Some("string1 custom field"))
          )
        )
      }

      "merge two cases" in
        pending
//      db.transaction { implicit graph =>
      //        Seq("#2", "#3").toTry(caseSrv.getOrFail) must beSuccessfulTry.which { cases: Seq[Case with Entity] ⇒
      //          val mergedCase = caseSrv.merge(cases)(graph, dummyUserSrv.getSystemAuthContext)
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

      "add custom field with wrong type" in db.transaction { implicit graph =>
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, "boolean1", Some("plop")) must beFailedTry
        }
      }

      "add custom field" in db.transaction { implicit graph =>
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, "boolean1", Some(true)) must beSuccessfulTry
          caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == true)
        }
      }

      "update custom field" in db.transaction { implicit graph =>
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setOrCreateCustomField(`case`, "boolean1", Some(false)) must beSuccessfulTry
          caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == false)
        }
      }

      "update case title" in db.transaction { implicit graph =>
        caseSrv.get("#4").update("title" -> "new title")
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          `case`.title must_=== "new title"
        }
      }

      "get correct next case number" in db.roTransaction { implicit graph =>
        caseSrv.nextCaseNumber shouldEqual 5
      }

      "close a case properly" in {
        val updates = Seq(PropertyUpdater(FPathElem("status"), CaseStatus.Resolved) { (vertex, _, _, _) =>
          vertex.property("status", CaseStatus.Resolved)
          Success(Json.obj("status" -> CaseStatus.Resolved))
        })

        val r = db.tryTransaction(implicit graph => caseSrv.update(caseSrv.get("#1"), updates))

        r must beSuccessfulTry

        val updatedCase = db.roTransaction(implicit graph => caseSrv.get("#1").getOrFail().get)
        updatedCase.status shouldEqual CaseStatus.Resolved
        updatedCase.endDate must beSome
      }

      "upsert case tags" in db.roTransaction { implicit graph =>
        val c3           = caseSrv.get("#3").getOrFail().get
        val existingTags = caseSrv.get(c3).tags.toList.map(_.value.get)

        existingTags must contain(exactly("t2", "t1"))

        val r = db.tryTransaction(
          implicit graph => caseSrv.updateTagNames(c3, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="yolo""""))
        )

        r must beSuccessfulTry
        db.roTransaction(implicit graph => caseSrv.get(c3).tags.toList.map(_.value.get) must contain(exactly("t2", "yolo")))
      }

      "add new tags and not previous ones" in {
        // Create a case with tags first
        val c = db
          .tryTransaction(
            implicit graph =>
              caseSrv.create(
                Case(0, "case 5", "desc 5", 1, new Date(), None, flag = false, 2, 3, CaseStatus.Open, None),
                None,
                orgaSrv.getOrFail("cert").get,
                tagSrv.initSteps.toList.toSet,
                Map.empty,
                None,
                Nil
              )
          )
          .get

        c.tags must not(beEmpty)

        val currentLen = c.tags.length

        db.tryTransaction(
          implicit graph => caseSrv.addTags(c.`case`, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""))
        ) must beSuccessfulTry

        db.roTransaction(implicit graph => {
          caseSrv.initSteps.has("title", "case 5").tags.toList.length shouldEqual currentLen + 1
        })
      }

      "add an observable if not existing" in db.roTransaction { implicit graph =>
        val c1          = caseSrv.get("#1").getOrFail().get
        val observables = observableSrv.initSteps.richObservable.toList

        observables must not(beEmpty)

        val hfr = observables.find(_.message.contains("Some weird domain")).get

        db.tryTransaction(implicit graph => {
            caseSrv.addObservable(c1, hfr)
          })
          .get must throwA[CreateError]

        val newObs = db
          .tryTransaction(
            implicit graph => {
              observableSrv.create(
                Observable(Some("if you feel lost"), 1, ioc = false, sighted = true),
                observableTypeSrv.get("domain").getOrFail().get,
                "lost.com",
                Set[String](),
                Nil
              )
            }
          )
          .get

        db.tryTransaction(implicit graph => {
          caseSrv.addObservable(c1, newObs)
        }) must beSuccessfulTry
      }

      "remove a case and its dependencies" in db.roTransaction { implicit graph =>
        val c1          = caseSrv.get("#1").getOrFail().get
        val observables = caseSrv.get("#1").observables.toList
        val tasks       = caseSrv.get("#1").tasks.toList

        observables must not(beEmpty)
        tasks must not(beEmpty)

        db.tryTransaction(implicit graph => caseSrv.cascadeRemove(c1)) must beSuccessfulTry
        db.roTransaction(implicit graph => {
          observableSrv.get(observables.head).exists() must beFalse
          taskSrv.get(tasks.head).exists() must beFalse
          caseSrv.get("#1").exists() must beFalse
        })
      }
    }
  }
}
