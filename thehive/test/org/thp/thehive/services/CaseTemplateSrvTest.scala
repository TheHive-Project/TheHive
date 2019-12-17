package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import scala.util.{Success, Try}

class CaseTemplateSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user5@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val caseTemplateSrv: CaseTemplateSrv  = app.instanceOf[CaseTemplateSrv]
    val db: Database                      = app.instanceOf[Database]
    val orgaSrv                           = app.instanceOf[OrganisationSrv]
    val userSrv                           = app.instanceOf[UserSrv]
    val tagSrv: TagSrv                    = app.instanceOf[TagSrv]
    val taskSrv: TaskSrv                  = app.instanceOf[TaskSrv]
    val customFieldSrv                    = app.instanceOf[CustomFieldSrv]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    def createTemplate(name: String, desc: String) = {
      val t = db.tryTransaction(
        implicit graph =>
          caseTemplateSrv.create(
            CaseTemplate(
              name,
              name,
              Some("[CTT]"),
              Some(desc),
              Some(2),
              flag = false,
              Some(1),
              Some(3),
              Some(s"summary $name")
            ),
            orgaSrv.getOrFail("cert").get,
            Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""),
            Seq(
              (
                Task(s"task case template $name", "group1", None, TaskStatus.Waiting, flag = false, None, None, 0, None),
                userSrv.get("user5@thehive.local").headOption()
              )
            ),
            Seq(("string1", Some("love")), ("boolean1", Some(false)))
          )
      )

      t must beSuccessfulTry

      t.get
    }

    s"[$name] case template service" should {
      "create a case template" in {
        val name = "case template test 1"
        createTemplate(name, "description ctt1")

        db.roTransaction(implicit graph => {
          tagSrv.initSteps.getByName("testNamespace", "testPredicate", Some("newOne")).exists() must beTrue
          taskSrv.initSteps.has("title", s"task case template $name").exists() must beTrue
          val richCT = caseTemplateSrv.initSteps.has("name", name).richCaseTemplate.getOrFail().get
          richCT.customFields.length shouldEqual 2
        })
      }

      "add a task to a template" in {
        val caseTemplate = createTemplate("case template test 2", "desc ctt2")

        caseTemplate.tasks.length shouldEqual 1

        val task1 = db.roTransaction(implicit graph => taskSrv.initSteps.has("title", "case 1 task 1").richTask.getOrFail().get)

        db.tryTransaction(implicit graph => caseTemplateSrv.addTask(caseTemplate.caseTemplate, task1.task)) must beSuccessfulTry
        val updatedCaseTemplate =
          db.roTransaction(implicit graph => caseTemplateSrv.initSteps.has("name", "case template test 2").richCaseTemplate.getOrFail())

        updatedCaseTemplate.get.tasks must contain(task1)
      }

      "update a case template" in {
        val caseTemplate = createTemplate("case template test 3", "desc ctt3")
        val updates = Seq(
          PropertyUpdater(FPathElem("name"), "updated") { (vertex, _, _, _) =>
            vertex.property("name", "updated")
            Success(Json.obj("name" -> "updated"))
          },
          PropertyUpdater(FPathElem("flag"), true) { (vertex, _, _, _) =>
            vertex.property("flag", true)
            Success(Json.obj("flag" -> true))
          }
        )

        db.tryTransaction(implicit graph => caseTemplateSrv.update(caseTemplateSrv.get(caseTemplate.caseTemplate), updates)) must beSuccessfulTry
        db.roTransaction(implicit graph => caseTemplateSrv.get(caseTemplate.caseTemplate).getOrFail()) must beSuccessfulTry.which(c => {
          c.name shouldEqual "updated"
          c.flag must beTrue
        })
      }

      "update case template tags" in {
        val caseTemplate = createTemplate("case template test 4", "desc ctt4")
        val newTags =
          Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne2"""", """newNspc.newPred="newOne3"""")

        db.tryTransaction(
          implicit graph =>
            caseTemplateSrv.updateTagNames(
              caseTemplate.caseTemplate,
              newTags
            )
        ) must beSuccessfulTry
        db.roTransaction(implicit graph => caseTemplateSrv.get(caseTemplate.caseTemplate).richCaseTemplate.getOrFail()) must beSuccessfulTry.which(
          c => c.tags.flatMap(_.value) must containTheSameElementsAs(Seq("t2", "newOne2", "newOne3"))
        )
      }

      "add tags to a case template" in {
        val caseTemplate = createTemplate("case template test 5", "desc ctt5")

        db.tryTransaction(
          implicit graph =>
            caseTemplateSrv
              .addTags(caseTemplate.caseTemplate, Set("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne2""""))
        ) must beSuccessfulTry

        db.roTransaction(implicit graph => caseTemplateSrv.get(caseTemplate.caseTemplate).richCaseTemplate.getOrFail()) must beSuccessfulTry.which(
          c => c.tags.flatMap(_.value) must containTheSameElementsAs(Seq("t2", "newOne", "newOne2"))
        )
      }

      "update/create case template custom fields" in db.roTransaction { implicit graph =>
        val caseTemplate = createTemplate("case template test 6", "desc ctt6")

        caseTemplate.customFields.flatMap(_.value) must containTheSameElementsAs(Seq("love", false))

        val string1  = customFieldSrv.get("string1").getOrFail().get
        val bool1    = customFieldSrv.get("boolean1").getOrFail().get
        val integer1 = customFieldSrv.get("integer1").getOrFail().get

        db.tryTransaction(
          implicit graph =>
            caseTemplateSrv
              .updateCustomField(caseTemplate.caseTemplate, Seq((string1, "hate"), (bool1, true), (integer1, 1)))
        ) must beSuccessfulTry

        db.roTransaction(implicit graph => caseTemplateSrv.get(caseTemplate.caseTemplate).richCaseTemplate.getOrFail()) must beSuccessfulTry.which(
          c => c.customFields.flatMap(_.value) must containTheSameElementsAs(Seq("hate", true, 1))
        )
      }

      "give access to case templates if permitted" in db.roTransaction { implicit graph =>
        caseTemplateSrv.initSteps.can(Permissions.manageCaseTemplate).toList must not(beEmpty)
        caseTemplateSrv
          .initSteps
          .can(Permissions.manageCaseTemplate)(DummyUserSrv(userId = "user1@thehive.local", organisation = "cert").authContext)
          .toList must beEmpty
      }

      "show only visible case templates" in db.roTransaction { implicit graph =>
        val certTemplate = createTemplate("case template test 7", "desc ctt7")
        val adminAuthCtx = DummyUserSrv(userId = "user3@thehive.local").authContext

        caseTemplateSrv.initSteps.get(certTemplate._id).visible.exists() must beTrue
        caseTemplateSrv.initSteps.get(certTemplate._id).visible(adminAuthCtx).exists() must beFalse
      }
    }
  }
}
