package org.thp.thehive.connector.cortex.services

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Organisation, Permissions}
import org.thp.thehive.services._
import play.api.test.{NoMaterializer, PlaySpecification}

import scala.util.Try

class ServiceHelperTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .bindToProvider[CortexClient, TestCortexClientProvider]
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

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
        r must contain(Organisation("default", "initial organisation"))

        val r2 = db.roTransaction { implicit graph =>
          serviceHelper
            .organisationFilter(
              app.instanceOf[OrganisationSrv].initSteps,
              Nil,
              Nil
            )
            .toList
        }
        r2 must contain(Organisation("default", "initial organisation"), Organisation("cert", "cert"))
      }

      "return the correct filtered CortexClient list" in {
        val client = app.instanceOf[CortexClient]
        val r      = serviceHelper.availableCortexClients(Seq(client), "default")

        r must contain(client)
      }
    }
  }
}
