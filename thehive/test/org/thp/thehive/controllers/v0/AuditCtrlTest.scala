package org.thp.thehive.controllers.v0

import java.util.Date

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{Case, CaseStatus, DatabaseBuilder, Permissions}
import org.thp.thehive.services.{CaseSrv, OrganisationSrv}
import play.api.libs.json.JsObject
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class AuditCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all, organisation = "admin")
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val auditCtrl = app.instanceOf[AuditCtrl]
    val caseSrv   = app.instanceOf[CaseSrv]
    val orgaSrv   = app.instanceOf[OrganisationSrv]
    val db        = app.instanceOf[Database]

    def getFlow(caseId: String) = {
      val request = FakeRequest("GET", "/api/v0/flow")
        .withHeaders("user" -> "user3@thehive.local", "X-Organisation" -> "admin")
      val result = auditCtrl.flow(Some(caseId), None)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      contentAsJson(result).as[List[JsObject]]
    }

    "return a list of audits including the last created one" in {
      // Check for no parasite audit
      getFlow("#4") must beEmpty

      // Create an event first
      val c = db
        .tryTransaction(
          implicit graph =>
            caseSrv.create(
              Case(0, "case audit", "desc audit", 1, new Date(), None, flag = false, 1, 1, CaseStatus.Open, None),
              None,
              orgaSrv.getOrFail("admin").get,
              Set.empty,
              Map.empty,
              None,
              Nil
            )(graph, dummyUserSrv.authContext)
        )
        .get

      // Get the actual data
      val l = getFlow(c._id)

      l must not(beEmpty)
    }
  }
}
