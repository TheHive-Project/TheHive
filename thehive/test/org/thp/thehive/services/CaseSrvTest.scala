package org.thp.thehive.services

import java.util.Date

import play.api.test.PlaySpecification

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models._

class CaseSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv()

  Fragments.foreach(new DatabaseProviders().list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(dummyUserSrv.initialAuthContext))
      .bindToProvider(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    DatabaseBuilder.build(app.instanceOf[TheHiveSchema])(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

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
          user = "toom",
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
          user = "admin",
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
        richCase.user must_=== "toom"
        richCase.customFields must contain(
          allOf(
            CustomFieldWithValue("boolean1", "boolean custom field", CustomFieldBoolean.name, true),
            CustomFieldWithValue("string1", "string custom field", CustomFieldString.name, "string1 custom field")
          ))
      }

      "merge two cases" in db.transaction { implicit graph ⇒
        val mergedCase = caseSrv.merge("#2", "#3")(graph, dummyUserSrv.initialAuthContext)

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
        mergedCase.user must_=== "test"
        mergedCase.customFields must contain(
          allOf(
            CustomFieldWithValue("boolean1", "boolean custom field", CustomFieldBoolean.name, true),
            CustomFieldWithValue("string1", "string custom field", CustomFieldString.name, "string1 custom field")
          ))
      }
    }
  }
}
