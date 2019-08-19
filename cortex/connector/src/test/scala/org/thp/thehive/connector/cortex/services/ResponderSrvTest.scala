package org.thp.thehive.connector.cortex.services

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{DatabaseBuilder, _}
import org.thp.thehive.services._
import play.api.test.{NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.Try

class ResponderSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] responder service" should {
      val responderSrv                      = app.instanceOf[ResponderSrv]
      val taskSrv: TaskSrv                  = app.instanceOf[TaskSrv]
      implicit val authContext: AuthContext = dummyUserSrv.authContext
      val db                                = app.instanceOf[Database]

      "fetch responders by type" in db.roTransaction { implicit graph =>
        val t = taskSrv.initSteps.toList.headOption

        t must beSome.which { task =>
          val r = await(responderSrv.getRespondersByType("case_task", task._id))

          r.find(_._1.name == "respTest1") must beSome
        }
      }
    }
}
