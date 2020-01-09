package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.services.OrganisationSrv

class OrganisationCtrlTest extends PlaySpecification with TestAppBuilder {
  "organisation controller" should {
    "create a new organisation" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description")))
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[OrganisationCtrl].create(request)
      status(result) must_=== 201
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "orga1"
    }

    "refuse to create an user if the permission doesn't contain ManageOrganisation right" in testApp { app =>
      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga2", "no description")))
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[OrganisationCtrl].create(request)
      status(result) must_=== 403
    }

    "get an organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/organisation/cert").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get("cert")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "cert"
    }

    "get a visible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/organisation/soc").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get("soc")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "soc"
    }

    "refuse to get a invisible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v1/organisation/${OrganisationSrv.administration.name}").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get(OrganisationSrv.administration.name)(request)
      status(result) must_=== 404
    }
  }
}
