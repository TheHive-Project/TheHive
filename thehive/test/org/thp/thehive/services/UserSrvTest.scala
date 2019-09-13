package org.thp.thehive.services

import scala.util.Try

import play.api.test.PlaySpecification

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class UserSrvTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv()
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val userSrv: UserSrv = app.instanceOf[UserSrv]
    val db: Database     = app.instanceOf[Database]

    s"[$name] user service" should {

      "create and get an user by his id" in db.transaction { implicit graph =>
        userSrv.createEntity(
          User(login = "getByIdTest", name = "test user (getById)", apikey = None, locked = false, password = None)
        ) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user._id) must beSuccessfulTry(user)
          }
      }

      "create and get an user by his login" in db.transaction { implicit graph =>
        userSrv.createEntity(
          User(login = "getByLoginTest", name = "test user (getByLogin)", apikey = None, locked = false, password = None)
        ) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user.login) must beSuccessfulTry(user)
          }
      }
    }
  }
}
