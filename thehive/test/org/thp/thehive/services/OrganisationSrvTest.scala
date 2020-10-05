package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

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
        app[OrganisationSrv].getOrFail(EntityName("cert"))
      } must beSuccessfulTry
    }
  }
}
