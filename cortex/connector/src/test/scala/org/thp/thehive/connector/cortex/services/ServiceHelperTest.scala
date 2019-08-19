package org.thp.thehive.connector.cortex.services

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CortexClient, CortexConfig, TestCortexClientProvider}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{DatabaseBuilder, Organisation, Permissions, TheHiveSchema}
import org.thp.thehive.services._
import play.api.test.{NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.concurrent.duration.DurationInt
import scala.util.Try

class ServiceHelperTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindToProvider[CortexClient, TestCortexClientProvider]
      .bindActor[ConfigActor]("config-actor")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val serviceHelper = app.instanceOf[ServiceHelper]
    val db            = app.instanceOf[Database]

    s"[$name] service helper" should {

      "filter properly organisations according to supplied config" in {
        val r = db.roTransaction { implicit graph =>
          serviceHelper
            .organisationFilter(
              app.instanceOf[OrganisationSrv].initSteps,
              List("*"),
              List("cert")
            )
            .toList
        }
        r must contain(Organisation("default"))

        val r2 = db.roTransaction { implicit graph =>
          serviceHelper
            .organisationFilter(
              app.instanceOf[OrganisationSrv].initSteps,
              Nil,
              Nil
            )
            .toList
        }
        r2 must contain(Organisation("default"), Organisation("cert"))
      }

      "return the correct filtered CortexClient list" in {
        val client       = app.instanceOf[CortexClient]
        val cortexConfig = CortexConfig(Map("test" -> client), 1.second, 0)
        val r            = serviceHelper.availableCortexClients(cortexConfig, Organisation("default"))

        r must contain(client)
      }

    }
  }
}
