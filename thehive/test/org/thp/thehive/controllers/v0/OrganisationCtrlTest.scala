package org.thp.thehive.controllers.v0

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.services.OrganisationSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class OrganisationCtrlTest extends PlaySpecification with TestAppBuilder {
  "organisation controller" should {
    "create a new organisation and bulk link several" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/organisation")
        .withJsonBody(Json.toJson(InputOrganisation("orga1", "no description")))
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[OrganisationCtrl].create(request)
      status(result) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[OutputOrganisation].name must_=== "orga1"

      val requestBulkLink = FakeRequest("PUT", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""{"organisations":["orga1"]}"""))
      val resultBulkLink = app[OrganisationCtrl].bulkLink("cert")(requestBulkLink)
      status(resultBulkLink) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(resultBulkLink)}")

      val requestLinks = FakeRequest("GET", s"/api/organisation/cert/links")
        .withHeaders("user" -> "admin@thehive.local")
      val resultLinks = app[OrganisationCtrl].listLinks("cert")(requestLinks)
      status(resultLinks) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultLinks)}")
      contentAsJson(resultLinks).as[List[OutputOrganisation]].map(_.name) must contain("orga1")
    }

    "refuse to create an organisation if the permission doesn't contain ManageOrganisation right" in testApp { app =>
      val request = FakeRequest("POST", "/api/v0/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga2", "no description")))
        .withHeaders("user" -> "certadmin@thehive.local")
      val result = app[OrganisationCtrl].create(request)
      status(result) must_=== 403
    }

    "get an organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/organisation/cert").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get("cert")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "cert"
    }

    "get a visible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/organisation/cert").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get("cert")(request)
      status(result) must_=== 200
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "cert"
    }

    "refuse to get a invisible organisation" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/organisation/FIXME").withHeaders("user" -> "certuser@thehive.local")
      val result  = app[OrganisationCtrl].get("FIXME")(request)
      status(result) must_=== 404
    }

    "update an organisation" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/organisation/admin")
        .withJsonBody(Json.parse(s"""
              {
                 "organisation":{
                    "name":"defaultUpdate"
                 }
              }
            """.stripMargin))
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[OrganisationCtrl].update("cert")(request)

      status(result) must_=== 204
    }

    "link organisations to soc organisation" in testApp { app =>
      val request = FakeRequest("PUT", s"/api/organisation/soc/link/cert")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[OrganisationCtrl].link("soc", "cert")(request)
      status(result) shouldEqual 201

      val requestLinks = FakeRequest("GET", s"/api/organisation/soc/links")
        .withHeaders("user" -> "admin@thehive.local")
      val resultLinks = app[OrganisationCtrl].listLinks("soc")(requestLinks)
      status(resultLinks) shouldEqual 200
      contentAsJson(resultLinks).as[List[OutputOrganisation]] must not(beEmpty)
    }

    "link and unlink organisations" in testApp { app =>
      val request = FakeRequest("PUT", s"/api/organisation/cert/link/admin")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[OrganisationCtrl].link("soc", "cert")(request)
      status(result) shouldEqual 201

      val requestLinks = FakeRequest("GET", s"/api/organisation/soc/links")
        .withHeaders("user" -> "socuser@thehive.local")
      val resultLinks = app[OrganisationCtrl].listLinks("soc")(requestLinks)
      status(resultLinks) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultLinks)}")
      contentAsJson(resultLinks).as[List[OutputOrganisation]].length must beEqualTo(1)

      val requestUnlink = FakeRequest("DELETE", s"/api/organisation/soc/link/cert")
        .withHeaders("user" -> "admin@thehive.local")
      val resultUnlink = app[OrganisationCtrl].unlink("soc", "cert")(requestUnlink)

      status(resultUnlink) shouldEqual 204

      val requestLinks2 = FakeRequest("GET", s"/api/organisation/soc/links")
        .withHeaders("user" -> "socuser@thehive.local")
      val resultLinks2 = app[OrganisationCtrl].listLinks("soc")(requestLinks2)
      status(resultLinks2) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultLinks2)}")
      contentAsJson(resultLinks2).as[List[OutputOrganisation]] must beEmpty
    }

    "update organisation name" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/v0/organisation/cert")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.obj("name" -> "cert2"))
      val result = app[OrganisationCtrl].update("cert")(request)
      status(result) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(result)}")
      app[Database].roTransaction { implicit graph =>
        app[OrganisationSrv].get("cert2").exists() must beTrue
      }
    }

    "fail to update admin organisation" in testApp { app =>
      val request = FakeRequest("PATCH", s"/api/v0/organisation/admin")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.obj("description" -> "new description"))
      val result = app[OrganisationCtrl].update("admin")(request)
      status(result) must_=== 400
    }

  }
}
