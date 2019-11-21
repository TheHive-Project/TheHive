package org.thp.thehive.services

import java.util.Date

import org.apache.tinkerpop.gremlin.process.traversal.Order
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.Try

class AuditSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val auditSrv: AuditSrv                = app.instanceOf[AuditSrv]
    val orgaSrv: OrganisationSrv          = app.instanceOf[OrganisationSrv]
    val caseSrv: CaseSrv                  = app.instanceOf[CaseSrv]
    val taskSrv: TaskSrv                  = app.instanceOf[TaskSrv]
    val shareSrv: ShareSrv                = app.instanceOf[ShareSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    s"[$name] audit service" should {
      "get main audits by ids and sorted" in db.roTransaction(implicit graph => {
        // Create 3 case events first
        val orgAdmin = orgaSrv.getOrFail("admin").get
        val c1 = db
          .tryTransaction(
            implicit graph =>
              caseSrv.create(
                Case(0, "case audit", "desc audit", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
                None,
                orgAdmin,
                Set.empty,
                Map.empty,
                None,
                Nil
              )
          )
          .get
        caseSrv.updateTagNames(c1.`case`, Set("lol"))
        db.tryTransaction(implicit graph => {
          val t = taskSrv.create(Task("test audit", "", None, TaskStatus.Waiting, flag = false, None, None, 0, None), None)
          shareSrv.shareTask(t.get, c1.`case`, orgAdmin)
        })
        val audits = auditSrv.initSteps.toList

        val r = auditSrv.getMainByIds(Order.asc, audits.map(_._id): _*).toList

        // Only the main ones
        r.length shouldEqual 2
        r.head shouldEqual audits.filter(_.mainAction).minBy(_._createdAt)
      })
    }
  }
}
