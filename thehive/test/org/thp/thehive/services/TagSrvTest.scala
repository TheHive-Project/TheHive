package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.models.Profile
import play.api.test.PlaySpecification

import scala.util.Success

class TagSrvTest extends PlaySpecification with TestAppBuilder {

  implicit val authContext: AuthContext =
    DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert", permissions = Profile.analyst.permissions).authContext

  "tag service" should {
    "fromString" should {
      "be parsed from namespace:predicate" in testApp { app =>
        import app.thehiveModule._

        tagSrv.fromString("namespace:predicate") must beSome("namespace", "predicate", None)
      }

      "be parsed from namespace:predicate=" in testApp { app =>
        import app.thehiveModule._

        tagSrv.fromString("namespace:predicate=") must beNone
      }

      "be parsed from namespace: predicate" in testApp { app =>
        import app.thehiveModule._

        tagSrv.fromString("namespace: predicate") must beSome("namespace", "predicate", None)
      }

      "be parsed from namespace:predicate=value" in testApp { app =>
        import app.thehiveModule._

        tagSrv.fromString("namespace:predicate=value") must beSome("namespace", "predicate", Some("value"))
      }
    }

    "getOrCreate" should {
      "get a tag from a taxonomy" in testApp { app =>
        import app._
        import app.thehiveModule._

        // TODO add tags property in Taxonomy.json to test get
        database.transaction { implicit graph =>
          val tag = tagSrv.getOrCreate("taxonomy1:pred1=value1")
          tag.map(_.toString) must beEqualTo(Success("taxonomy1:pred1=\"value1\""))
        }
      }

      "get a _freetag tag" in testApp { app =>
        import app._
        import app.thehiveModule._

        database.transaction { implicit graph =>
          val orgId = organisationSrv.currentId.value
          val tag   = tagSrv.getOrCreate("afreetag")
          tag.map(_.namespace) must beEqualTo(Success(s"_freetags_$orgId"))
          tag.map(_.predicate) must beEqualTo(Success("afreetag"))
          tag.map(_.predicate) must beEqualTo(Success("afreetag"))
          tag.map(_.colour)    must beEqualTo(Success(tagSrv.freeTagColour))
        }
      }
    }

  }
}
