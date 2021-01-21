package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.TagOps._
import play.api.libs.json.{JsNumber, JsString, JsTrue, JsValue}
import play.api.test.PlaySpecification

class CaseTemplateSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authcontext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  "case template service" should {
    "create a case template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[CaseTemplateSrv].create(
          caseTemplate = CaseTemplate(
            name = "case template test 1",
            displayName = "case template test 1",
            titlePrefix = Some("[CTT]"),
            description = Some("description ctt1"),
            severity = Some(2),
            tags = Seq("""testNamespace:testPredicate="t2"""", """testNamespace:testPredicate="newOne""""),
            flag = false,
            tlp = Some(1),
            pap = Some(3),
            summary = Some("summary case template test 1")
          ),
          organisation = app[OrganisationSrv].getOrFail(EntityName("cert")).get,
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

      app[Database].roTransaction { implicit graph =>
        app[TagSrv].startTraversal.getByName("testNamespace", "testPredicate", Some("newOne")).exists must beTrue
        app[TaskSrv].startTraversal.has(_.title, "task case template case template test 1").exists must beTrue
        val richCT = app[CaseTemplateSrv].startTraversal.getByName("case template test 1").richCaseTemplate.getOrFail("CaseTemplate").get
        richCT.customFields.length shouldEqual 2
      }
    }

    "add a task to a template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          caseTemplate <- app[CaseTemplateSrv].getOrFail(EntityName("spam"))
          _ <- app[CaseTemplateSrv].createTask(
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

      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get(EntityName("spam")).tasks.has(_.title, "t1").exists
      } must beTrue
    }

    "update case template tags" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          caseTemplate <- app[CaseTemplateSrv].getOrFail(EntityName("spam"))
          _ <- app[CaseTemplateSrv].updateTags(
            caseTemplate,
            Set("""testNamespace:testPredicate="t2"""", """testNamespace:testPredicate="newOne2"""", """newNspc.newPred="newOne3"""")
          )
        } yield ()
      } must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get(EntityName("spam")).tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq("testNamespace:testPredicate=\"t2\"", "testNamespace:testPredicate=\"newOne2\"", "newNspc:newPred=\"newOne3\"")
      )
    }

    "add tags to a case template" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          caseTemplate <- app[CaseTemplateSrv].getOrFail(EntityName("spam"))
          _            <- app[CaseTemplateSrv].addTags(caseTemplate, Set("""testNamespace:testPredicate="t2"""", """testNamespace:testPredicate="newOne2""""))
        } yield ()
      } must beSuccessfulTry
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get(EntityName("spam")).tags.toList.map(_.toString)
      } must containTheSameElementsAs(
        Seq(
          "testNamespace:testPredicate=\"t2\"",
          "testNamespace:testPredicate=\"newOne2\"",
          "testNamespace:testPredicate=\"spam\"",
          "testNamespace:testPredicate=\"src:mail\""
        )
      )
    }

    "update/create case template custom fields" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        for {
          string1      <- app[CustomFieldSrv].getOrFail(EntityName("string1"))
          bool1        <- app[CustomFieldSrv].getOrFail(EntityName("boolean1"))
          integer1     <- app[CustomFieldSrv].getOrFail(EntityName("integer1"))
          caseTemplate <- app[CaseTemplateSrv].getOrFail(EntityName("spam"))
          _            <- app[CaseTemplateSrv].updateCustomField(caseTemplate, Seq((string1, JsString("hate")), (bool1, JsTrue), (integer1, JsNumber(1))))
        } yield ()
      } must beSuccessfulTry

      val expected: Seq[(String, JsValue)] = Seq("string1" -> JsString("hate"), "boolean1" -> JsTrue, "integer1" -> JsNumber(1))
      app[Database].roTransaction { implicit graph =>
        app[CaseTemplateSrv].get(EntityName("spam")).customFields.nameJsonValue.toSeq
      } must contain(exactly(expected: _*))
    }
  }
}
