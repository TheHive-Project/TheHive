package org.thp.thehive.services

import play.api.test.PlaySpecification
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, UserSrv => SUserSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models._

import scala.util.Try

class UserSrvTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv()
  implicit val authContext: AuthContext = dummyUserSrv.initialAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bindInstance[SUserSrv](dummyUserSrv)
      .bindToProvider(dbProvider)
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val userSrv: UserSrv = app.instanceOf[UserSrv]
    val db: Database     = app.instanceOf[Database]

    s"[$name] user service" should {

      "create and get an user by his id" in db.transaction { implicit graph =>
        userSrv.create(User(login = "getByIdTest", name = "test user (getById)", apikey = None, locked = false, password = None)) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user._id) must beSuccessfulTry(user)
          }
      }

      "create and get an user by his login" in db.transaction { implicit graph =>
        userSrv.create(User(login = "getByLoginTest", name = "test user (getByLogin)", apikey = None, locked = false, password = None)) must beSuccessfulTry
          .which { user =>
            userSrv.getOrFail(user.login) must beSuccessfulTry(user)
          }
      }
    }
  }
}
