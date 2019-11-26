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
    val tagSrv: TagSrv                       = app.instanceOf[TagSrv]
    val taskSrv: TaskSrv                     = app.instanceOf[TaskSrv]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    s"[$name] case template service" should {
      "create a case template" in {
        db.tryTransaction(
          implicit graph =>
            caseTemplateSrv.create(
              CaseTemplate(
                "case template test 1",
                "ctt 1",
                Some("[CTT]"),
                Some("desc ctt 1"),
                Some(2),
                flag = false,
                Some(1),
                Some(3),
                Some("summary ctt1")
              ),
              orgaSrv.getOrFail("cert").get,
              Seq("""testNamespace.testPredicate="t2"""", """testNamespace.testPredicate="newOne""""),
              Seq(
                (
                  Task("task case template 1", "group1", None, TaskStatus.Waiting, flag = false, None, None, 0, None),
                  userSrv.get("user5@thehive.local").headOption()
                )
              ),
              Seq(("string1", Some("love")), ("boolean1", Some(false)))
            )
        ) must beSuccessfulTry

        db.roTransaction(implicit graph => {
          tagSrv.initSteps.getByName("testNamespace", "testPredicate", Some("newOne")).exists() must beTrue
          taskSrv.initSteps.has("title", "task case template 1").exists() must beTrue
          val richCT = caseTemplateSrv.initSteps.has("name", "case template test 1").richCaseTemplate.getOrFail().get
          richCT.customFields.length shouldEqual 2
        })
      }
    }
  }
}
