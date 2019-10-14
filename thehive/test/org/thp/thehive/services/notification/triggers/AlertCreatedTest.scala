package org.thp.thehive.services.notification.triggers

import java.util.Date

import scala.util.Try

import play.api.libs.json.{JsObject, Json}
import play.api.test.{FakeRequest, PlaySpecification}

import gremlin.scala.{Key, P}
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.controllers.v0.AlertCtrl
import org.thp.thehive.dto.v0.{InputAlert, OutputAlert}
import org.thp.thehive.models._
import org.thp.thehive.services.{AlertSrv, AuditSrv, OrganisationSrv, UserSrv}

class AlertCreatedTest extends PlaySpecification {
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
    s"[$name] alert created trigger" should {
      val db: Database = app.instanceOf[Database]
      val alertCtrl    = app.instanceOf[AlertCtrl]
      val alertSrv     = app.instanceOf[AlertSrv]
      val userSrv      = app.instanceOf[UserSrv]
      val auditSrv     = app.instanceOf[AuditSrv]
      val orgSrv       = app.instanceOf[OrganisationSrv]

      "be properly triggered on alert creation" in db.roTransaction { implicit graph =>
        val request = FakeRequest("POST", "/api/v0/alert")
          .withJsonBody(
            Json
              .toJson(
                InputAlert(
                  `type` = "test",
                  source = "alert_creation_test",
                  sourceRef = "#1",
                  externalLink = None,
                  title = "alert title (create alert test)",
                  description = "alert description (create alert test)",
                  severity = Some(2),
                  date = new Date(),
                  tags = Set("tag1", "tag2"),
                  flag = Some(false),
                  tlp = Some(1),
                  pap = Some(3)
                )
              )
              .as[JsObject]
          )
          .withHeaders("user" -> "user1@thehive.local")

        val result = alertCtrl.create(request)

        status(result) should equalTo(201)

        val alertOutput = contentAsJson(result).as[OutputAlert]
        val alert       = alertSrv.get(alertOutput.id).getOrFail()

        alert must beSuccessfulTry

        val audit = auditSrv.initSteps.has(Key("objectId"), P.eq(alert.get._id)).getOrFail()

        audit must beSuccessfulTry

        val orga = orgSrv.get("cert").getOrFail()

        orga must beSuccessfulTry

        val user2 = userSrv.initSteps.getByName("user2@thehive.local").getOrFail()
        val user1 = userSrv.initSteps.getByName("user1@thehive.local").getOrFail()

        user2 must beSuccessfulTry
        user1 must beSuccessfulTry

        val alertCreated = new AlertCreated()

        alertCreated.filter(audit.get, Some(alert.get), orga.get, user1.get) must beFalse
        alertCreated.filter(audit.get, Some(alert.get), orga.get, user2.get) must beTrue
      }
    }

}
