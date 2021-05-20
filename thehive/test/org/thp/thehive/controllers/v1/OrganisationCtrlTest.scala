package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.Organisation
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class OrganisationCtrlTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  "organisation controller" should {
    "create a new organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description")))
        .withHeaders("user" -> "admin@thehive.local")
      val result = organisationCtrl.create(request)
      status(result) must_=== 201
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "orga1"
    }

    "refuse to create an user if the permission doesn't contain ManageOrganisation right" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga2", "no description")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = organisationCtrl.create(request)
      status(result) must_=== 403
    }

    "get an organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", s"/api/v1/organisation/cert").withHeaders("user" -> "certuser@thehive.local")
      val result  = organisationCtrl.get("cert")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "cert"
    }

    "get a visible organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", s"/api/v1/organisation/soc").withHeaders("user" -> "certuser@thehive.local")
      val result  = organisationCtrl.get("soc")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "soc"
    }

    "refuse to get a invisible organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", s"/api/v1/organisation/${Organisation.administration.name}").withHeaders("user" -> "certuser@thehive.local")
      val result  = organisationCtrl.get(Organisation.administration.name)(request)
      status(result) must_=== 404
    }

    "update organisation name" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV1._

      val request = FakeRequest("PATCH", s"/api/v1/organisation/cert")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.obj("name" -> "cert2"))
      val result = organisationCtrl.update("cert")(request)
      status(result) must_=== 204
      database.roTransaction { implicit graph =>
        organisationSrv.get(EntityName("cert2")).exists must beTrue
      }
    }

    "fail to update admin organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("PATCH", s"/api/v1/organisation/admin")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.obj("description" -> "new description"))
      val result = organisationCtrl.update("admin")(request)
      status(result) must_=== 400
    }
  }
}
