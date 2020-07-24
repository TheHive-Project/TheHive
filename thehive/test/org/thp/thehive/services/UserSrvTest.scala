package org.thp.thehive.services

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.{Failure, Success}

class UserSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").getSystemAuthContext

  "user service" should {

    "create and get an user by his id" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[UserSrv].createEntity(
          User(login = "getByIdTest", name = "test user (getById)", apikey = None, locked = false, password = None, totpSecret = None)
        ) must beSuccessfulTry
          .which { user =>
            app[UserSrv].getOrFail(user._id) must beSuccessfulTry(user)
          }
      }
    }

    "create and get an user by his login" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[UserSrv].createEntity(
          User(
            login = "getbylogintest@thehive.local",
            name = "test user (getByLogin)",
            apikey = None,
            locked = false,
            password = None,
            totpSecret = None
          )
        ) must beSuccessfulTry
          .which { user =>
            app[UserSrv].getOrFail(user.login) must beSuccessfulTry(user)
          }
      }
    }

    "deduplicate users in an organisation" in testApp { app =>
      val db              = app[Database]
      val userSrv         = app[UserSrv]
      val organisationSrv = app[OrganisationSrv]
      val profileSrv      = app[ProfileSrv]
      val roleSrv         = app[RoleSrv]
      db.tryTransaction { implicit graph =>
        val certadmin = userSrv.get("certadmin@thehive.local").head()
        val cert      = organisationSrv.get("cert").head()
        val analyst   = profileSrv.get("analyst").head()
        roleSrv.create(certadmin, cert, analyst).get
        val userCount = userSrv.get("certadmin@thehive.local").organisations.get("cert").getCount
        if (userCount == 2) Success(())
        else Failure(new Exception(s"User certadmin is not in cert organisation twice ($userCount)"))
      }
      new UserIntegrityCheckOps(db, userSrv, profileSrv, organisationSrv, roleSrv).check()
      db.roTransaction { implicit graph =>
        val userCount = userSrv.get("certadmin@thehive.local").organisations.get("cert").getCount
        userCount must beEqualTo(1)
      }
    }
  }
}
