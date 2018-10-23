package org.thp.thehive.services

import java.util.Date

import org.specs2.specification.core.Fragments
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models._
import play.api.test.PlaySpecification

class CaseSrvTest extends PlaySpecification {
  val dummyUserSrv             = DummyUserSrv()
  val authContext: AuthContext = dummyUserSrv.initialAuthContext

  Fragments.foreach(DatabaseProviders.list) { dbProvider ⇒
    s"[${dbProvider.name}] case service" should {
      val app: AppBuilder = AppBuilder()
        .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
        .bindInstance[InitialAuthContext](InitialAuthContext(authContext))
        .bindToProvider(dbProvider)
      app.instanceOf[DatabaseBuilder]
      val caseSrv: CaseSrv = app.instanceOf[CaseSrv]
      val db: Database     = app.instanceOf[Database]

      "list all cases" in db.transaction { implicit graph ⇒
        caseSrv.initSteps.toList.map(_.number) must contain(allOf(1, 2, 3, 4))
      }

      "get a case without impact status" in db.transaction { implicit graph ⇒
        // val richCase = TheHiveSchema.`case`.filter(EntityFilter[Vertex, Case](_.has(Key("number") of 1))).richCase.head()
        val caseId   = caseSrv.getOrFail("#1")._id
        val richCase = caseSrv.get(caseId).richCase.head()
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
          tags = Nil,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.open,
          summary = None,
          impactStatus = None,
          user = "toom",
          organisation = "default",
          Nil
        )
      }

      "get a case with impact status" in db.transaction { implicit graph ⇒
        val richCase = caseSrv.get("#2").richCase.head()
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
          tags = Nil,
          flag = false,
          tlp = 2,
          pap = 2,
          status = CaseStatus.open,
          summary = None,
          impactStatus = Some("NoImpact"),
          user = "admin",
          organisation = "default",
          Nil
        )
        richCase._createdBy must_=== authContext.userId
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
            CustomFieldValue("boolean1", "boolean custom field", CustomFieldBoolean.name, true),
            CustomFieldValue("string1", "string custom field", CustomFieldString.name, "string1 custom field")
          ))

      }
    }
  }
}
