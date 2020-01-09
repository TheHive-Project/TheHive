package org.thp.thehive.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class OrganisationSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").authContext

  "organisation service" should {
    "create and get an organisation by his id" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[OrganisationSrv].create(Organisation(name = "orga1", "no description")) must beSuccessfulTry.which { organisation =>
          app[OrganisationSrv].getOrFail(organisation._id) must beSuccessfulTry(organisation)
        }
      }
    }

    "create and get an organisation by its name" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[OrganisationSrv].create(Organisation(name = "orga2", "no description")) must beSuccessfulTry.which { organisation =>
          app[OrganisationSrv].getOrFail(organisation.name) must beSuccessfulTry(organisation)
        }
      }
    }
  }
}
