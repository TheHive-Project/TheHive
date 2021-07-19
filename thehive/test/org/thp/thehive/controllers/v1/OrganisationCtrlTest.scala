package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.dto.v1.{InputOrganisation, OrganisationLink, OutputOrganisation, OutputSharingProfile}
import org.thp.thehive.models.Organisation
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}
import eu.timepit.refined.auto._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.services.SharingRule

class OrganisationCtrlTest extends PlaySpecification with TestAppBuilder with TraversalOps {
  "organisation controller" should {
    "create a new organisation" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga1", "no description", None, None)))
        .withHeaders("user" -> "admin@thehive.local")
      val result = organisationCtrl.create(request)
      status(result) must_=== 201
      val resultOrganisation = contentAsJson(result).as[OutputOrganisation]
      resultOrganisation.name must_=== "orga1"
    }

    "refuse to create an user if the permission doesn't contain ManageOrganisation right" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("POST", "/api/v1/organisation")
        .withJsonBody(Json.toJson(InputOrganisation(name = "orga2", "no description", None, None)))
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

      val request = FakeRequest("PATCH", "/api/v1/organisation/cert")
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

      val request = FakeRequest("PATCH", "/api/v1/organisation/admin")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.obj("description" -> "new description"))
      val result = organisationCtrl.update("admin")(request)
      status(result) must_=== 400
    }

    "list sharing profiles" in testApp { app =>
      import app.thehiveModuleV1._

      val request = FakeRequest("GET", "/api/v1/sharingProfile")
        .withHeaders("user" -> "admin@thehive.local")
      val result = organisationCtrl.listSharingProfiles()(request)
      status(result)                                                                  must beEqualTo(200)
      contentAsJson(result).as[Seq[OutputSharingProfile]].exists(_.name == "default") must beTrue
    }

    "link organisations to soc organisation with default sharing profile" in testApp { app =>
      import app.thehiveModuleV1._

      { // unlink soc/cert
        val request = FakeRequest("PUT", s"/api/organisation/soc/link/cert")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.unlink("soc", "cert")(request)
        status(result) must beEqualTo(204)
      }
      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations must beEmpty
      }
      { // link with default sharing profile
        val request = FakeRequest("PUT", "/api/organisation/soc/link/cert")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.link("soc", "cert")(request)
        status(result) must beEqualTo(201)
      }
      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations.size must beEqualTo(1)
      }
      { // check sharing profiles
        val request = FakeRequest("GET", "/api/organisation/soc")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.get("soc")(request)
        status(result) must beEqualTo(200)
        val organisation = contentAsJson(result).as[OutputOrganisation]
        organisation.links.size must beEqualTo(1)
        organisation.links.head must beEqualTo(OrganisationLink("cert", "default", "default"))
      }
    }

    "link organisations to soc organisation with custom sharing profile" in testApp { app =>
      import app.thehiveModuleV1._

      { // unlink soc/cert
        val request = FakeRequest("PUT", s"/api/organisation/soc/link/cert")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.unlink("soc", "cert")(request)
        status(result) must beEqualTo(204)
      }
      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations must beEmpty
      }
      { // link with custom sharing profile
        val request = FakeRequest("PUT", "/api/organisation/soc/link/cert")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.obj("linkType" -> "type1", "otherLinkType" -> "type2"))
        val result = organisationCtrl.link("soc", "cert")(request)
        status(result) must beEqualTo(201)
      }
      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations.size must beEqualTo(1)
      }
      { // check sharing profiles
        val request = FakeRequest("GET", "/api/organisation/soc")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.get("soc")(request)
        status(result) must beEqualTo(200)
        val organisation = contentAsJson(result).as[OutputOrganisation]
        organisation.links.size must beEqualTo(1)
        organisation.links.head must beEqualTo(OrganisationLink("cert", "type1", "type2"))
      }
    }

    "update a link" in testApp { app =>
      import app.thehiveModuleV1._

      { // link with custom sharing profile
        val request = FakeRequest("PUT", "/api/organisation/soc/link/cert")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.obj("linkType" -> "type1", "otherLinkType" -> "type2"))
        val result = organisationCtrl.link("soc", "cert")(request)
        status(result) must beEqualTo(201)
      }
      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations.size must beEqualTo(1)
      }
      { // check sharing profiles
        val request = FakeRequest("GET", "/api/organisation/soc")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.get("soc")(request)
        status(result) must beEqualTo(200)
        val organisation = contentAsJson(result).as[OutputOrganisation]
        organisation.links.size must beEqualTo(1)
        organisation.links.head must beEqualTo(OrganisationLink("cert", "type1", "type2"))
      }
    }

    "bulk link organisations" in testApp { app =>
      import app.thehiveModuleV1._
      import app.thehiveModule._

      app
        .database
        .tryTransaction { implicit graph =>
          implicit val authContext: AuthContext = DummyUserSrv().authContext
          organisationSrv.create(Organisation("testOrga", "test organisation", SharingRule.default, SharingRule.default))
        }
        .get

      { // bulk link with custom sharing profile
        val request = FakeRequest("PUT", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.obj("organisations" -> Seq("cert", "testOrga"), "linkType" -> "type1", "otherLinkType" -> "type2"))
        val result = organisationCtrl.bulkLink("soc")(request)
        status(result) must beEqualTo(201)
      }

      { // check links
        val request = FakeRequest("GET", "/api/organisation/soc/links")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.listLinks("soc")(request)
        status(result) must beEqualTo(200)
        val linkedOrganisations = contentAsJson(result).as[List[OutputOrganisation]]
        linkedOrganisations.size must beEqualTo(2)
      }

      { // check sharing profiles
        val request = FakeRequest("GET", "/api/organisation/soc")
          .withHeaders("user" -> "admin@thehive.local")
        val result = organisationCtrl.get("soc")(request)
        status(result) must beEqualTo(200)
        val organisation = contentAsJson(result).as[OutputOrganisation]
        organisation.links.size must beEqualTo(2)
        organisation.links      must contain(OrganisationLink("cert", "type1", "type2"), OrganisationLink("testOrga", "type1", "type2"))
      }
    }
  }
}
