package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.Try

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
            Seq("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""),
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
    }
  }
}
