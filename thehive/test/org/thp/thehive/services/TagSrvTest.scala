package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.Profile
import play.api.test.PlaySpecification

import scala.util.Success

class TagSrvTest extends PlaySpecification with TestAppBuilder {

  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Profile.analyst.permissions).authContext

  "tag service" should {
    "fromString" should {
      "be parsed from namespace:predicate" in testApp { app =>
        app[TagSrv].fromString("namespace:predicate") must beEqualTo(Some("namespace", "predicate", None))
      }

      "be parsed from namespace:predicate=" in testApp { app =>
        app[TagSrv].fromString("namespace:predicate=") must beEqualTo(None)
      }

      "be parsed from namespace: predicate" in testApp { app =>
        app[TagSrv].fromString("namespace: predicate") must beEqualTo(Some("namespace", "predicate", None))
      }

      "be parsed from namespace:predicate=value" in testApp { app =>
        app[TagSrv].fromString("namespace:predicate=value") must beEqualTo(Some("namespace", "predicate", Some("value")))
      }
    }

    "getOrCreate" should {
      "get a tag from a taxonomy" in testApp { app =>
        app[Database].roTransaction { implicit graph =>
          val tag = app[TagSrv].getOrCreate("taxonomy1:pred1=value1")
          tag.map(_.toString) must beEqualTo(Success("taxonomy1:pred1=\"value1\""))
        }
      }

      "get a _freetag tag" in testApp { app =>
        app[Database].transaction { implicit graph =>
          val orgId = app[OrganisationSrv].currentId.value
          val tag   = app[TagSrv].getOrCreate("afreetag")
          tag.map(_.namespace) must beEqualTo(Success(s"_freetags_$orgId"))
          tag.map(_.predicate) must beEqualTo(Success("afreetag"))
          tag.map(_.predicate) must beEqualTo(Success("afreetag"))
          tag.map(_.colour) must beEqualTo(Success(app[TagSrv].defaultColour))
        }
      }
    }

  }
}
