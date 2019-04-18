package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.UserSrv
import org.thp.scalligraph.controllers.{AuthenticateSrv, TestAuthenticateSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models._
import org.thp.thehive.services.LocalUserSrv

class OrganisationCtrlTest extends PlaySpecification with Mockito {
  val config: Configuration = Configuration.load(Environment.simple())

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindToProvider(dbProvider)
      .bind[AuthenticateSrv, TestAuthenticateSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[UserSrv, LocalUserSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Unit =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], app.instanceOf[UserSrv].initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val organisationCtrl: OrganisationCtrl = app.instanceOf[OrganisationCtrl]
    implicit lazy val mat: Materializer    = app.instanceOf[Materializer]

    s"[$name] organisation controller" should {

      "create a new organisation" in {
        val request = FakeRequest("POST", "/api/v1/organisation")
          .withJsonBody(Json.toJson(InputOrganisation(name = "orga1")))
          .withHeaders("user" → "admin")
        val result = organisationCtrl.create(request)
        status(result) must_=== 201
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "orga1"
      }

      "refuse to create an user if the permission doesn't contain ManageOrganisation right" in {
        val request = FakeRequest("POST", "/api/v1/organisation")
          .withJsonBody(Json.toJson(InputOrganisation(name = "orga2")))
          .withHeaders("user" → "user1")
        val result = organisationCtrl.create(request)
        status(result) must_=== 403
      }

      "get an organisation" in {
        val request = FakeRequest("GET", s"/api/v1/organisation/cert").withHeaders("user" → "user1")
        val result  = organisationCtrl.get("cert")(request)
        status(result) must_=== 200
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "cert"
      }

      "get a visible organisation" in {
        val request = FakeRequest("GET", s"/api/v1/organisation/cert").withHeaders("user" → "user3")
        val result  = organisationCtrl.get("cert")(request)
        status(result) must_=== 200
        val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
        resultOrganisation.name must_=== "cert"
      }

      "refuse to get a invisible organisation" in {
        val request = FakeRequest("GET", s"/api/v1/user/default").withHeaders("user" → "user1")
        val result  = organisationCtrl.get("default")(request)
        status(result) must_=== 404
      }
    }
  }
}
