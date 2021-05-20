package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.models._
import play.api.libs.json.{JsNumber, JsString, JsTrue, JsValue}
import play.api.test.PlaySpecification

class CaseTemplateSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authcontext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "case template service" should {
    "create a case template" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        caseTemplateSrv.create(
          caseTemplate = CaseTemplate(
            name = "case template test 1",
            displayName = "case template test 1",
            titlePrefix = Some("[CTT]"),
            description = Some("description ctt1"),
            severity = Some(2),
            tags = Seq("""t2""", """newOne"""),
            flag = false,
            tlp = Some(1),
            pap = Some(3),
            summary = Some("summary case template test 1")
          ),
          organisation = organisationSrv.getOrFail(EntityName("cert")).get,
          tasks = Seq(
            Task(
              title = "task case template case template test 1",
              group = "group1",
              description = None,
              status = TaskStatus.Waiting,
              flag = false,
              startDate = None,
              endDate = None,
              order = 0,
              dueDate = None,
              assignee = None
            )
          ),
          customFields = Seq(("string1", Some("love")), ("boolean1", Some(false)))
        )
      } must beASuccessfulTry

      database.roTransaction { implicit graph =>
        val orgId = organisationSrv.currentId.value
        tagSrv.startTraversal.getByName(s"_freetags_$orgId", "newOne", None).exists           must beTrue
        taskSrv.startTraversal.has(_.title, "task case template case template test 1").exists must beTrue
        val richCT = caseTemplateSrv.startTraversal.getByName("case template test 1").richCaseTemplate.getOrFail("CaseTemplate").get
        richCT.customFields.length shouldEqual 2
      }
    }

    "add a task to a template" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          caseTemplate <- caseTemplateSrv.getOrFail(EntityName("spam"))
          _ <- caseTemplateSrv.createTask(
            caseTemplate,
            Task(
              title = "t1",
              group = "default",
              description = None,
              status = TaskStatus.Waiting,
              flag = false,
              startDate = None,
              endDate = None,
              order = 1,
              dueDate = None,
              assignee = None
            )
          )
        } yield ()
      } must beSuccessfulTry

      database.roTransaction { implicit graph =>
        caseTemplateSrv.get(EntityName("spam")).tasks.has(_.title, "t1").exists
      } must beTrue
    }

    "update case template tags" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          caseTemplate <- caseTemplateSrv.getOrFail(EntityName("spam"))
          _ <- caseTemplateSrv.updateTags(
            caseTemplate,
            Set("""t2""", """newOne2""", """newNspc:newPred="newOne3"""")
          )
        } yield ()
      } must beSuccessfulTry
      database.roTransaction { implicit graph =>
        caseTemplateSrv.get(EntityName("spam")).tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq("t2", "newOne2", "newNspc:newPred=\"newOne3\"")
      )
    }

    "add tags to a case template" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          caseTemplate <- caseTemplateSrv.getOrFail(EntityName("spam"))
          _            <- caseTemplateSrv.addTags(caseTemplate, Set("""t2""", """newOne2"""))
        } yield ()
      } must beSuccessfulTry
      database.roTransaction { implicit graph =>
        caseTemplateSrv.get(EntityName("spam")).tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq(
          "t2",
          "newOne2",
          "spam",
          "src:mail"
        )
      )
    }

    "update/create case template custom fields" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          string1      <- customFieldSrv.getOrFail(EntityName("string1"))
          bool1        <- customFieldSrv.getOrFail(EntityName("boolean1"))
          integer1     <- customFieldSrv.getOrFail(EntityName("integer1"))
          caseTemplate <- caseTemplateSrv.getOrFail(EntityName("spam"))
          _ <- caseTemplateSrv.updateCustomField(
            caseTemplate,
            Seq((string1, JsString("hate"), None), (bool1, JsTrue, None), (integer1, JsNumber(1), None))
          )
        } yield ()
      } must beSuccessfulTry

      val expected: Seq[(String, JsValue)] = Seq("string1" -> JsString("hate"), "boolean1" -> JsTrue, "integer1" -> JsNumber(1))
      database.roTransaction { implicit graph =>
        caseTemplateSrv.get(EntityName("spam")).customFields.nameJsonValue.toSeq
      } must contain(exactly(expected: _*))
    }
  }
}
