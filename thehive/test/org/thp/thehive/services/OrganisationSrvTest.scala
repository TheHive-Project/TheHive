package org.thp.thehive.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class OrganisationSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").authContext

  "organisation service" should {
    "create an organisation" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[OrganisationSrv].create(Organisation(name = "orga1", "no description"))
      } must beSuccessfulTry
    }

    "get an organisation by its name" in testApp { app =>
      app[Database].tryTransaction { implicit graph =>
        app[OrganisationSrv].getOrFail("cert")
      } must beSuccessfulTry
    }
  }
}
