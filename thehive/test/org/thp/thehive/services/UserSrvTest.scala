package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.DummyUserSrv
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.{Failure, Success}

class UserSrvTest extends PlaySpecification with TestAppBuilder with TheHiveOpsNoDeps {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").getSystemAuthContext

  "user service" should {

    "create and get an user by his id" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        userSrv.createEntity(
          User(
            login = "getByIdTest",
            name = "test user (getById)",
            email = None,
            resetSecret = None,
            apikey = None,
            locked = false,
            password = None,
            totpSecret = None
          )
        ) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user._id) must beSuccessfulTry(user)
          }
      }
    }

    "create and get an user by his login" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.transaction { implicit graph =>
        userSrv.createEntity(
          User(
            login = "getbylogintest@thehive.local",
            name = "test user (getByLogin)",
            email = None,
            resetSecret = None,
            apikey = None,
            locked = false,
            password = None,
            totpSecret = None
          )
        ) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user._id) must beSuccessfulTry(user)
          }
      }
    }

    "deduplicate users in an organisation" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        val certadmin = userSrv.get(EntityName("certadmin@thehive.local")).head
        val cert      = organisationSrv.get(EntityName("cert")).head
        val analyst   = profileSrv.get(EntityName("analyst")).head
        roleSrv.create(certadmin, cert, analyst).get
        val userCount = userSrv.get(EntityName("certadmin@thehive.local")).organisations.get(EntityName("cert")).getCount
        if (userCount == 2) Success(())
        else Failure(new Exception(s"User certadmin is not in cert organisation twice ($userCount)"))
      }
      new UserIntegrityCheckOps(database, userSrv, profileSrv, organisationSrv, roleSrv).duplicationCheck()
      database.roTransaction { implicit graph =>
        val userCount = userSrv.get(EntityName("certadmin@thehive.local")).organisations.get(EntityName("cert")).getCount
        userCount must beEqualTo(1)
      }
    }
  }
}
