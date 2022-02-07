package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.scalligraph.services.KillSwitch
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import play.api.test.PlaySpecification

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class UserSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").getSystemAuthContext

  "user service" should {

    "create and get an user by his id" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[UserSrv].createEntity(
          User(
            login = "getByIdTest",
            name = "test user (getById)",
            apikey = None,
            locked = false,
            password = None,
            totpSecret = None,
            failedAttempts = None,
            lastFailed = None
          )
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
            totpSecret = None,
            failedAttempts = None,
            lastFailed = None
          )
        ) must beSuccessfulTry
          .which { user =>
            app[UserSrv].getOrFail(user._id) must beSuccessfulTry(user)
          }
      }
    }

    "deduplicate users in an organisation" in testApp { app =>
      implicit val db: Database = app[Database]
      val userSrv               = app[UserSrv]
      val organisationSrv       = app[OrganisationSrv]
      val profileSrv            = app[ProfileSrv]
      val roleSrv               = app[RoleSrv]
      db.tryTransaction { implicit graph =>
        val certadmin = userSrv.get(EntityName("certadmin@thehive.local")).head
        val cert      = organisationSrv.get(EntityName("cert")).head
        val analyst   = profileSrv.get(EntityName("analyst")).head
        roleSrv.create(certadmin, cert, analyst).get
        val userCount = userSrv.get(EntityName("certadmin@thehive.local")).organisations.get(EntityName("cert")).getCount
        if (userCount == 2) Success(())
        else Failure(new Exception(s"User certadmin is not in cert organisation twice ($userCount)"))
      }
      new UserIntegrityCheck(db, userSrv, profileSrv, organisationSrv, roleSrv).runGlobalCheck(5.minutes, KillSwitch.alwaysOn)
      db.roTransaction { implicit graph =>
        val userCount = userSrv.get(EntityName("certadmin@thehive.local")).organisations.get(EntityName("cert")).getCount
        userCount must beEqualTo(1)
      }
    }
  }
}
