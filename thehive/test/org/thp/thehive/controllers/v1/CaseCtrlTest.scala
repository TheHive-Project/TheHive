package org.thp.thehive.controllers.v1

import eu.timepit.refined.auto._
import io.scalaland.chimney.dsl.TransformerOps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.scalligraph.{EntityIdOrName, EntityName}
import org.thp.thehive.TestApplication
import org.thp.thehive.dto.v1.{InputCase, InputShare, OutputCase, OutputCustomFieldValue}
import org.thp.thehive.models.{Observable, Organisation, Share, Task}
import org.thp.thehive.services.{TheHiveOps, WithTheHiveModule}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import play.api.test.{FakeRequest, PlaySpecification}

import java.util.Date
import scala.util.Success

case class TestCustomFieldValue(name: String, description: String, `type`: String, value: JsValue, order: Int)

object TestCustomFieldValue {
  def apply(outputCustomFieldValue: OutputCustomFieldValue): TestCustomFieldValue =
    outputCustomFieldValue.into[TestCustomFieldValue].transform
}

case class TestCase(
    title: String,
    description: String,
    severity: Int,
    startDate: Date,
    endDate: Option[Date] = None,
    tags: Set[String] = Set.empty,
    flag: Boolean,
    tlp: Int,
    pap: Int,
    status: String,
    summary: Option[String] = None,
    impactStatus: Option[String] = None,
    resolutionStatus: Option[String] = None,
    user: Option[String],
    customFields: Seq[TestCustomFieldValue] = Seq.empty
)

object TestCase {
  def apply(outputCase: OutputCase): TestCase =
    outputCase
      .into[TestCase]
      .withFieldRenamed(_.assignee, _.user)
      .withFieldComputed(_.customFields, _.customFields.map(TestCustomFieldValue.apply).sortBy(_.order))
      .transform
}

class CaseCtrlTest extends PlaySpecification with TestAppBuilder {
  "case controller" should {
    "create a new case" in testApp { app =>
      import app.thehiveModuleV1._

      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJson(
            InputCase(
              title = "case title (create case test)",
              description = "case description (create case test)",
              severity = Some(2),
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3),
              user = Some("certro@thehive.local")
            )
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = caseCtrl.create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "case title (create case test)",
        description = "case description (create case test)",
        severity = 2,
        startDate = now,
        endDate = None,
        tags = Set("tag1", "tag2"),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        summary = None,
        user = Some("certro@thehive.local"),
        customFields = Seq.empty
      )

      TestCase(resultCase) must_=== expected
    }

    "create a new case using a template" in testApp { app =>
      import app.thehiveModuleV1._

      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJsObject(
            InputCase(
              title = "case title (create case test with template)",
              description = "case description (create case test with template)",
              severity = None,
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3)
            )
          ) + ("caseTemplate" -> JsString("spam"))
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = caseCtrl.create(request)
      status(result) must_=== 201
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "[SPAM] case title (create case test with template)",
        description = "case description (create case test with template)",
        severity = 1,
        startDate = now,
        endDate = None,
        tags = Set("tag1", "tag2", "spam", "src:mail"),
        flag = false,
        tlp = 1,
        pap = 3,
        status = "Open",
        summary = None,
        user = Some("certuser@thehive.local"),
        customFields = Seq(
          TestCustomFieldValue("string1", "string custom field", "string", JsString("string1 custom field"), 0),
          TestCustomFieldValue("boolean1", "boolean custom field", "boolean", JsNull, 1)
        )
      )

      TestCase(resultCase) must_=== expected
    }

    "create case automatically shared with other organisation" in testApp { app =>
      import app.thehiveModule._
      import app.thehiveModuleV1._

      // Create link between 2 orgs and set sharing profiles and sharing rules
      app.database.tryTransaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv().authContext
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._
          for {
            soc <- organisationSrv.getOrFail(EntityName("soc"))
            cert <-
              organisationSrv
                .get(EntityName("cert"))
                .update(_.taskRule, "existingOnly")
                .update(_.observableRule, "upcomingOnly")
                .getOrFail("Organisation")
            _ <- organisationSrv.link(soc, cert, "supervised", "supervised")
          } yield ()
        }
      }

      // Create a case
      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJson(
            InputCase(
              title = "case title (shared)",
              description = "case description (shared)",
              severity = Some(2),
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3),
              user = Some("certro@thehive.local")
            )
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = caseCtrl.create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val caseId = EntityIdOrName(contentAsJson(result).as[OutputCase]._id)

      app.database.tryTransaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc").authContext
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._

          caseSrv.get(caseId).visible.exists must beTrue
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "soc")).head must beEqualTo(
            Share(owner = false, taskRule = "all", observableRule = "all")
          )
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "cert")).head must beEqualTo(
            Share(owner = true, taskRule = "existingOnly", observableRule = "upcomingOnly")
          )
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "soc")).profile.value(_.name).head must beEqualTo("analyst")
        }
        Success(())
      } must beASuccessfulTry
    }

    "create case automatically shared with other organisation with sharing parameter" in testApp { app =>
      import app.thehiveModule._
      import app.thehiveModuleV1._

      // Create link between 2 orgs and set sharing profiles and sharing rules
      // and create a unlinked org
      app.database.tryTransaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv().authContext
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._
          for {
            soc <- organisationSrv.getOrFail(EntityName("soc"))
            cert <-
              organisationSrv
                .get(EntityName("cert"))
                .update(_.taskRule, "existingOnly")
                .update(_.observableRule, "upcomingOnly")
                .getOrFail("Organisation")
            _ <- organisationSrv.link(soc, cert, "supervised", "supervised")
            _ <- organisationSrv.create(Organisation("unlinkedOrg", "Unlinked organisation", "manual", "manual"))
          } yield ()
        }
      }

      // Create a case
      val now = new Date()
      val request = FakeRequest("POST", "/api/v1/case")
        .withJsonBody(
          Json.toJsObject(
            InputCase(
              title = "case title (shared with parameter)",
              description = "case description (shared with parameter)",
              severity = Some(2),
              startDate = Some(now),
              tags = Set("tag1", "tag2"),
              flag = Some(false),
              tlp = Some(1),
              pap = Some(3),
              user = Some("certro@thehive.local")
            )
          ) ++ Json.obj(
            "sharingParameters" -> Seq(
              InputShare(
                organisation = "unlinkedOrg",
                share = Some(true),
                profile = Some("analyst"),
                taskRule = Some("existingOnly"),
                observableRule = Some("upcomingOnly")
              ),
              InputShare(
                organisation = "soc",
                share = Some(false),
                profile = Some("read-only"),
                taskRule = Some("manual"),
                observableRule = Some("manual")
              )
            ),
            "taskRule"       -> "manual",
            "observableRule" -> "manual"
          )
        )
        .withHeaders("user" -> "certuser@thehive.local")
      val result = caseCtrl.create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      val caseId = EntityIdOrName(contentAsJson(result).as[OutputCase]._id)

      app.database.tryTransaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv(organisation = "soc").authContext
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._

          caseSrv.get(caseId).organisations.value(_.name).toSeq must contain(exactly("cert", "soc", "unlinkedOrg"))
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "soc")).head must beEqualTo(
            Share(owner = false, taskRule = "all", observableRule = "all") // parameter is ignored because sharing profile is not editable
          )
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "cert")).head must beEqualTo(
            Share(owner = true, taskRule = "manual", observableRule = "manual")
          )
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "unlinkedOrg")).head must beEqualTo(
            Share(owner = false, taskRule = "existingOnly", observableRule = "upcomingOnly")
          )
          caseSrv.get(caseId).shares.filter(_.organisation.has(_.name, "soc")).profile.value(_.name).head must beEqualTo("analyst")
        }
        Success(())
      } must beASuccessfulTry
    }

    "get a case" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", s"/api/v1/case/1")
        .withHeaders("user" -> "certuser@thehive.local")
      val result     = caseCtrl.get("1")(request)
      val resultCase = contentAsJson(result).as[OutputCase]
      val expected = TestCase(
        title = "case#1",
        description = "description of case #1",
        severity = 2,
        startDate = new Date(1531667370000L),
        endDate = None,
        tags = Set("t1", "t3"),
        flag = false,
        tlp = 2,
        pap = 2,
        status = "Open",
        user = Some("certuser@thehive.local")
      )

      TestCase(resultCase) must_=== expected
    }

    "update a case" in testApp { app =>
//        val updateRequest = FakeRequest("PATCH", s"/api/v1/case/#2")
//          .withJsonBody(
//            Json.obj(
//              "title"  → "new title",
//              "flag"   → false,
//              "tlp"    → 2,
//              "pap"    → 1,
//              "status" → "resolved"
//            ))
//          .withHeaders("user" → "certuser@thehive.local")
//        val updateResult = caseCtrl.update("#2")(updateRequest)
//        status(updateResult) must_=== 204
//
//        val getRequest = FakeRequest("GET", s"/api/v1/case/#2")
//        val getResult  = caseCtrl.get("#2")(getRequest)
//        val resultCase = contentAsJson(getResult).as[OutputCase]
//        val expected = TestCase(
//          title = "new title",
//          description = "case description (update case test)",
//          severity = 2,
//          startDate = new Date(),
//          endDate = None,
//          tags = Set("tag1", "tag2"),
//          flag = false,
//          tlp = 2,
//          pap = 1,
//          status = "resolved",
//          user = Some(dummyUserSrv.authContext.userId)
//        )

//        TestCase(resultCase) must_=== expected
      pending
    }

    "merge 3 cases correctly" in testApp { app =>
      import app.thehiveModuleV1._

      val request21 = FakeRequest("GET", s"/api/v1/case/#21")
        .withHeaders("user" -> "certuser@thehive.local")
      val case21 = caseCtrl.get("21")(request21)
      status(case21) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(case21)}")
      val output21 = contentAsJson(case21).as[OutputCase]

      val request = FakeRequest("GET", "/api/v1/case/_merge/21,22,23")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = caseCtrl.merge("21,22,23")(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")

      val outputCase = contentAsJson(result).as[OutputCase]

      // Merge result
      TestCase(outputCase) must equalTo(
        TestCase(
          title = "case#21 / case#22 / case#23",
          description = "description of case #21\n\ndescription of case #22\n\ndescription of case #23",
          severity = 3,
          startDate = output21.startDate,
          endDate = output21.endDate,
          Set("toMerge:pred1=\"value1\"", "toMerge:pred2=\"value2\""),
          flag = true,
          tlp = 4,
          pap = 3,
          status = "Open",
          None,
          None,
          None,
          Some("certuser@thehive.local"),
          Seq()
        )
      )

      // Merged cases should be deleted
      val deleted21 = caseCtrl.get("21")(request)
      status(deleted21) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted21)}")
      val deleted22 = caseCtrl.get("22")(request)
      status(deleted22) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted22)}")
      val deleted23 = caseCtrl.get("23")(request)
      status(deleted23) must beEqualTo(404).updateMessage(s => s"$s\n${contentAsString(deleted23)}")
    }

    "merge two cases error, not same organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", "/api/v1/case/_merge/21,24")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = caseCtrl.merge("21,24")(request)
      // User shouldn't be able to see others cases, resulting in 404
      status(result) must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "merge two cases error, not same profile" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", "/api/v1/case/_merge/21,25")
        .withHeaders("user" -> "certuser@thehive.local")

      val result = caseCtrl.merge("21,25")(request)
      status(result)                              must beEqualTo(400).updateMessage(s => s"$s\n${contentAsString(result)}")
      (contentAsJson(result) \ "type").as[String] must beEqualTo("BadRequest")
    }

    "update sharing rule" in testApp { app =>
      import app.thehiveModule._
      import app.thehiveModuleV1._

      app.database.tryTransaction { implicit graph =>
        implicit val authContext: AuthContext = DummyUserSrv().authContext
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._
          for {
            share   <- caseSrv.getByName("2").share(EntityName("soc")).getOrFail("Organisation")
            analyst <- profileSrv.getByName("analyst").getOrFail("Profile")
            _       <- shareSrv.updateProfile(share, analyst)
          } yield ()
        }
      }

      {
        val request = FakeRequest("PATCH", "/api/v1/case/2")
          .withHeaders("user" -> "certuser@thehive.local")
          .withJsonBody(Json.obj("taskRule" -> "ruleA", "observableRule" -> "ruleB"))
        val result = caseCtrl.update("2")(request)
        status(result) must beEqualTo(204)
      }

      {
        val request = FakeRequest("PATCH", "/api/v1/case/2")
          .withHeaders("user" -> "socuser@thehive.local")
          .withJsonBody(Json.obj("taskRule" -> "ruleC", "observableRule" -> "ruleD"))
        val result = caseCtrl.update("2")(request)
        status(result) must beEqualTo(204)
      }

      app.database.roTransaction { implicit graph =>
        TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
          import ops._
          caseSrv.getByName("2").share(EntityName("cert")).head must beEqualTo(Share(owner = true, taskRule = "ruleA", observableRule = "ruleB"))
          caseSrv.getByName("2").share(EntityName("soc")).head  must beEqualTo(Share(owner = false, taskRule = "ruleC", observableRule = "ruleD"))
        }
      }
    }

    def testSharingRule(taskRule: String, observableRule: String)(implicit
        app: TestApplication with WithTheHiveModule with WithTheHiveModuleV1
    ): (Seq[String], Seq[String], Seq[String], Seq[String]) = {
      import app.thehiveModule._
      import app.thehiveModuleV1._

      TheHiveOps(organisationSrv, customFieldSrv, customFieldValueSrv) { ops =>
        import ops._

        app
          .database
          .tryTransaction { implicit graph =>
            implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext
            for {
              case2 <- caseSrv.getByName("2").getOrFail("Case")
              _     <- caseSrv.createTask(case2, Task(title = "beforeUpdate"))
              _ <- caseSrv.createObservable(
                case2,
                Observable(
                  message = Some("TEST"),
                  tlp = 2,
                  ioc = false,
                  sighted = false,
                  ignoreSimilarity = Some(false),
                  dataType = "other",
                  tags = Nil
                ),
                "beforeUpdate"
              )
              shareId <- caseSrv.getByName("2").share._id.getOrFail("Share")
            } yield shareId.toString
          }
          .get

        val request = FakeRequest("PATCH", "/api/v1/case/2")
          .withHeaders("user" -> "certuser@thehive.local")
          .withJsonBody(Json.obj("taskRule" -> taskRule, "observableRule" -> observableRule))
        val result = caseCtrl.update("2")(request)
        status(result) must beEqualTo(204)

        app
          .database
          .tryTransaction { implicit graph =>
            implicit val authContext: AuthContext = DummyUserSrv(organisation = "cert").authContext
            for {
              case2 <- caseSrv.getByName("2").getOrFail("Case")
              _     <- caseSrv.createTask(case2, Task(title = "afterUpdate"))
              _ <- caseSrv.createObservable(
                case2,
                Observable(
                  message = Some("TEST"),
                  tlp = 2,
                  ioc = false,
                  sighted = false,
                  ignoreSimilarity = Some(false),
                  dataType = "other",
                  tags = Nil
                ),
                "afterUpdate"
              )
              shareId <- caseSrv.getByName("2").share._id.getOrFail("Share")
            } yield shareId.toString
          }
          .get

        app.database.roTransaction { implicit graph =>
          val certObservables = caseSrv.getByName("2").observables(EntityName("cert")).value(_.data).toSeq
          val certTasks       = caseSrv.getByName("2").tasks(EntityName("cert")).value(_.title).toSeq
          val socObservables  = caseSrv.getByName("2").observables(EntityName("soc")).value(_.data).toSeq
          val socTasks        = caseSrv.getByName("2").tasks(EntityName("soc")).value(_.title).toSeq

          (certObservables, certTasks, socObservables, socTasks)
        }
      }
    }

    "apply sharing rules (all) when it is updated" in testApp { implicit app =>
      val (certObservables, certTasks, socObservables, socTasks) = testSharingRule("all", "all")
      certObservables must contain(exactly("beforeUpdate", "afterUpdate"))
      certTasks       must contain(exactly("beforeUpdate", "afterUpdate", "case 2 task 2"))
      socObservables  must contain(exactly("beforeUpdate", "afterUpdate"))
      socTasks        must contain(exactly("beforeUpdate", "afterUpdate", "case 2 task 2", "case 2 task 1"))
    }

    "apply sharing rules (manual) when it is updated" in testApp { implicit app =>
      val (certObservables, certTasks, socObservables, socTasks) = testSharingRule("manual", "manual")
      certObservables must contain(exactly("beforeUpdate", "afterUpdate"))
      certTasks       must contain(exactly("beforeUpdate", "afterUpdate", "case 2 task 2"))
      socObservables  must beEmpty
      socTasks        must contain(exactly("case 2 task 1"))
    }

    "apply sharing rules (existingOnly) when it is updated" in testApp { implicit app =>
      val (certObservables, certTasks, socObservables, socTasks) = testSharingRule("existingOnly", "existingOnly")
      certObservables must contain(exactly("beforeUpdate", "afterUpdate"))
      certTasks       must contain(exactly("beforeUpdate", "afterUpdate", "case 2 task 2"))
      socObservables  must contain(exactly("beforeUpdate"))
      socTasks        must contain(exactly("beforeUpdate", "case 2 task 2", "case 2 task 1"))
    }

    "apply sharing rules (upcomingOnly) when it is updated" in testApp { implicit app =>
      val (certObservables, certTasks, socObservables, socTasks) = testSharingRule("upcomingOnly", "upcomingOnly")
      certObservables must contain(exactly("beforeUpdate", "afterUpdate"))
      certTasks       must contain(exactly("beforeUpdate", "afterUpdate", "case 2 task 2"))
      socObservables  must contain(exactly("afterUpdate"))
      socTasks        must contain(exactly("afterUpdate", "case 2 task 1"))
    }
  }

}
