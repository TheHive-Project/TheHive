package org.thp.thehive.services

import play.api.test.PlaySpecification

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class UserSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "admin@thehive.local").getSystemAuthContext

  "user service" should {

    "create and get an user by his id" in testApp { app =>
      app[Database].transaction { implicit graph =>
        app[UserSrv].createEntity(
          User(login = "getByIdTest", name = "test user (getById)", apikey = None, locked = false, password = None)
        ) must beSuccessfulTry
          .which { user =>
            app[UserSrv].getOrFail(user._id) must beSuccessfulTry(user)
          }
      }

      "create and get an user by his login" in testApp { app =>
        app[Database].transaction { implicit graph =>
          app[UserSrv].createEntity(
            User(login = "getByLoginTest@thehive.local", name = "test user (getByLogin)", apikey = None, locked = false, password = None)
          ) must beSuccessfulTry
            .which { user =>
              app[UserSrv].getOrFail(user.login) must beSuccessfulTry(user)
            }
        }
      }
    }
  }
}
