package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class OrganisationCtrlTest extends PlaySpecification with Mockito {
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], app.instanceOf[UserSrv].getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val organisationCtrl: OrganisationCtrl = app.instanceOf[OrganisationCtrl]

    s"[$name] organisation controller" should {

      "create a new organisation" in {
        val request = FakeRequest("POST", "/api/v0/organisation")
          .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description")))
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.create(request)
        status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "orga1"
      }

      "refuse to create an organisation if the permission doesn't contain ManageOrganisation right" in {
        val request = FakeRequest("POST", "/api/v0/organisation")
          .withJsonBody(Json.toJson(InputOrganisation(name = "orga2", "no description")))
          .withHeaders("user" -> "user1@thehive.local")
        val result = organisationCtrl.create(request)
        status(result) must_=== 403
      }

      "get an organisation" in {
        val request = FakeRequest("GET", s"/api/v0/organisation/cert").withHeaders("user" -> "user1@thehive.local")
        val result  = organisationCtrl.get("cert")(request)
        status(result) must_=== 200
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "cert"
      }

      "get a visible organisation" in {
        val request = FakeRequest("GET", s"/api/v0/organisation/cert").withHeaders("user" -> "user1@thehive.local")
        val result  = organisationCtrl.get("cert")(request)
        status(result) must_=== 200
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "cert"
      }

      "refuse to get a invisible organisation" in {
        val request = FakeRequest("GET", s"/api/v0/user/default").withHeaders("user" -> "user1@thehive.local")
        val result  = organisationCtrl.get("default")(request)
        status(result) must_=== 404
      }

      "update an organisation" in {
        val request = FakeRequest("PATCH", s"/api/organisation/default")
          .withJsonBody(Json.parse(s"""
              {
                 "organisation":{
                    "name":"defaultUpdate"
                 }
              }
            """.stripMargin))
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.update("default")(request)

        status(result) must_=== 204
      }
    }
  }
}
