package org.thp.thehive.services
import org.specs2.specification.core.Fragments
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.models._
import play.api.test.PlaySpecification

class UserSrvTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv()
  implicit val authContext: AuthContext = dummyUserSrv.initialAuthContext

  Fragments.foreach(DatabaseProviders.list) { dbProvider ⇒
    val app: AppBuilder = AppBuilder()
      .bindInstance[org.thp.scalligraph.auth.UserSrv](dummyUserSrv)
      .bindInstance[InitialAuthContext](InitialAuthContext(authContext))
      .bindToProvider(dbProvider)
//      .bind[DatabaseBuilder, DatabaseBuilder]
    val userSrv: UserSrv = app.instanceOf[UserSrv]
    val db: Database     = app.instanceOf[Database]
    app.instanceOf[DatabaseBuilder]

    s"[${dbProvider.name}] user service" should {
      "create and get an user by his id" in db.transaction { implicit graph ⇒
        val user =
          userSrv.create(
            User(login = "getByIdTest", name = "test user (getById)", apikey = None, permissions = Nil, status = UserStatus.ok, password = None))
        userSrv.getOrFail(user._id) must_=== user
      }

      "create and get an user by his login" in db.transaction { implicit graph ⇒
        val user = userSrv.create(
          User(login = "getByLoginTest", name = "test user (getByLogin)", apikey = None, permissions = Nil, status = UserStatus.ok, password = None))
        userSrv.getOrFail(user.login) must_=== user
      }
    }
  }
}
