package org.thp.thehive.services.notification.triggers

import scala.util.Try

import play.api.test.PlaySpecification

import gremlin.scala.{Key, P}
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services._

class TaskAssignedTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv(userId = "user1@thehive.local")
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] task assigned trigger" should {
      val db: Database = app.instanceOf[Database]
      val taskSrv      = app.instanceOf[TaskSrv]
      val userSrv      = app.instanceOf[UserSrv]
      val auditSrv     = app.instanceOf[AuditSrv]
      val orgSrv       = app.instanceOf[OrganisationSrv]

      "be properly triggered on task assignment" in db.roTransaction { implicit graph =>
        val task1 = taskSrv.initSteps.has(Key("title"), P.eq("case 1 task 1")).getOrFail()
        val user2 = userSrv.initSteps.getByName("user2@thehive.local").getOrFail()
        val user1 = userSrv.initSteps.getByName("user1@thehive.local").getOrFail()

        task1 must beSuccessfulTry
        user2 must beSuccessfulTry
        user1 must beSuccessfulTry

        val taskAssigned = db.tryTransaction(implicit graph => taskSrv.assign(task1.get, user2.get))

        taskAssigned must beSuccessfulTry

        val audit = auditSrv.initSteps.has(Key("objectId"), P.eq(task1.get._id)).getOrFail()

        audit must beSuccessfulTry

        val orga = orgSrv.get("cert").getOrFail()

        orga must beSuccessfulTry

        val taskAssignedTrigger = new TaskAssigned(taskSrv)

        db.roTransaction(implicit graph => {
          taskAssignedTrigger.filter(audit.get, Some(task1.get), orga.get, user2.get) must beTrue
          taskAssignedTrigger.filter(audit.get, Some(task1.get), orga.get, user1.get) must beFalse
        })
      }
    }

}
