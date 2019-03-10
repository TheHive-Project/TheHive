package org.thp.thehive.services

import java.util.Date

import play.api.test.PlaySpecification
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{AppBuilder, BadRequestError}
import org.thp.thehive.models._

class CaseSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv()

  Fragments.foreach(new DatabaseProviders().list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
      .bind[Schema, TheHiveSchema]
      .bindToProvider(dbProvider)
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseSrv: CaseSrv = app.instanceOf[CaseSrv]
    val db: Database     = app.instanceOf[Database]

    s"[$name] case service" should {

      "list all cases" in db.transaction { implicit graph ⇒
        caseSrv.initSteps.toList().map(_.number) must contain(allOf(1, 2, 3, 4))
      }

      "get a case without impact status" in db.transaction { implicit graph ⇒
        // val richCase = TheHiveSchema.`case`.filter(EntityFilter[Vertex, Case](_.has(Key("number") of 1))).richCase.head()
        val caseId   = caseSrv.getOrFail("#1")._id
        val richCase = caseSrv.get(caseId).richCase.head()
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
          tags = Nil,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.open,
          summary = None,
          impactStatus = None,
          resolutionStatus = None,
          user = Some("toom"),
          organisation = "cert",
          Nil
        )
      }

      "get a case with impact status" in db.transaction { implicit graph ⇒
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
          tags = Nil,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.open,
          summary = None,
          impactStatus = Some("NoImpact"),
          resolutionStatus = None,
          user = Some("admin"),
          organisation = "cert",
          Nil
        )
        richCase._createdBy must_=== dummyUserSrv.userId
      }

      "get a case with custom fields" in db.transaction { implicit graph ⇒
        val richCase = caseSrv.get("#3").richCase.head()
        richCase.number must_=== 3
        richCase.title must_=== "case#3"
        richCase.description must_=== "description of case #3"
        richCase.severity must_=== 2
        richCase.startDate must_=== new Date(1531667370000L)
        richCase.endDate must beNone
        richCase.tags must_=== Nil
        richCase.flag must_=== false
        richCase.tlp must_=== 2
        richCase.pap must_=== 2
        richCase.status must_=== CaseStatus.open
        richCase.summary must beNone
        richCase.impactStatus must beNone
        richCase.user must beSome("toom")
        CustomField("boolean1", "boolean custom field", CustomFieldBoolean)
        richCase.customFields.map(f ⇒ (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
          ))
      }

      "merge two cases" in db.transaction { implicit graph ⇒
        val cases      = Seq("#2", "#3").map(caseSrv.getOrFail)
        val mergedCase = caseSrv.merge(cases)(graph, dummyUserSrv.initialAuthContext)

        mergedCase.title must_=== "case#2 / case#3"
        mergedCase.description must_=== "description of case #2\n\ndescription of case #3"
        mergedCase.severity must_=== 2
        mergedCase.startDate must_=== new Date(1531667370000L)
        mergedCase.endDate must beNone
        mergedCase.tags must_=== Nil
        mergedCase.flag must_=== false
        mergedCase.tlp must_=== 2
        mergedCase.pap must_=== 2
        mergedCase.status must_=== CaseStatus.open
        mergedCase.summary must beNone
        mergedCase.impactStatus must beNone
        mergedCase.user must beSome("test")
        mergedCase.customFields.map(f ⇒ (f.name, f.typeName, f.value)) must contain(
          allOf[(String, String, Option[Any])](
            ("boolean1", "boolean", Some(true)),
            ("string1", "string", Some("string1 custom field"))
          ))
      }

      "add custom field with wrong type" in db.transaction { implicit graph ⇒
        val `case` = caseSrv.getOrFail("#4")
        caseSrv.setCustomField(`case`, "boolean1", "plop")(graph, dummyUserSrv.initialAuthContext) must throwA[BadRequestError]
      }

      "add custom field" in db.transaction { implicit graph ⇒
        val `case` = caseSrv.getOrFail("#4")
        caseSrv.setCustomField(`case`, "boolean1", true)(graph, dummyUserSrv.initialAuthContext)
        caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == true)
      }

      "update custom field" in db.transaction { implicit graph ⇒
        val `case` = caseSrv.getOrFail("#4")
        caseSrv.setCustomField(`case`, "boolean1", false)(graph, dummyUserSrv.initialAuthContext)
        caseSrv.getCustomField(`case`, "boolean1").flatMap(_.value) must beSome.which(_ == false)
      }
    }
  }
}
