package org.thp.thehive.services

import java.util.Date

import play.api.test.PlaySpecification
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, UserSrv => SUserSrv}
import org.thp.scalligraph.models.{DatabaseBuilder => _, _}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models._

import scala.util.Try

class CaseSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv()

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bindInstance[SUserSrv](dummyUserSrv)
      .bind[Schema, TheHiveSchema]
      .bindToProvider(dbProvider)
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bindActor[ConfigActor]("config-actor")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseSrv: CaseSrv                  = app.instanceOf[CaseSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

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
          user = Some("user1"),
          Nil
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
          user = Some("user2"),
          Nil
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
        richCase.tags must contain(exactly(Tag("t1"), Tag("t2")))
        richCase.flag must_=== false
        richCase.tlp must_=== 2
        richCase.pap must_=== 2
        richCase.status must_=== CaseStatus.Open
        richCase.summary must beNone
        richCase.impactStatus must beNone
        richCase.user must beSome("user1")
        CustomField("boolean1", "boolean custom field", CustomFieldBoolean)
        richCase.customFields.map(f => (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
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
          caseSrv.setCustomField(`case`, "boolean1", "plop") must beFailedTry
        }
      }

      "add custom field" in db.transaction { implicit graph =>
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setCustomField(`case`, "boolean1", true) must beSuccessfulTry
          caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == true)
        }
      }

      "update custom field" in db.transaction { implicit graph =>
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          caseSrv.setCustomField(`case`, "boolean1", false) must beSuccessfulTry
          caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == false)
        }
      }

      "update case title" in db.transaction { implicit graph =>
        caseSrv.get("#4").update("title" -> "new title")
        caseSrv.getOrFail("#4") must beSuccessfulTry.which { `case`: Case with Entity =>
          `case`.title must_=== "new title"
        }
      }
    }
  }
}
