package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.{JsBoolean, JsString}
import play.api.test.PlaySpecification

import scala.util.Try

class ConfigSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val configSrv: ConfigSrv              = app.instanceOf[ConfigSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    s"[$name] config service" should {
      "set/get values" in {
        db.tryTransaction(implicit graph => {
          configSrv.organisation.setConfigValue("cert", "test", JsBoolean(true))
          configSrv.user.setConfigValue("user1@thehive.local", "test2", JsString("lol"))
        })

        db.roTransaction(implicit graph => {
          configSrv.organisation.getConfigValue("cert", "test") must beSome.which(c => c.value.as[Boolean] must beTrue)
          configSrv.user.getConfigValue("user1@thehive.local", "test2") must beSome.which(c => c.value.as[String] shouldEqual "lol")
        })
      }
    }
  }
}
