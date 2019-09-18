package org.thp.thehive.services

import scala.util.Try

import play.api.test.PlaySpecification

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._

class OrganisationSrvTest extends PlaySpecification {
  val dummyUserSrv                      = DummyUserSrv(userId = "admin@thehive.local")
  implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], authContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val organisationSrv: OrganisationSrv = app.instanceOf[OrganisationSrv]
    val db: Database                     = app.instanceOf[Database]

    s"[$name] organisation service" should {

      "create and get an organisation by his id" in db.transaction { implicit graph =>
        organisationSrv.create(Organisation(name = "orga1", "no description")) must beSuccessfulTry.which { organisation =>
          organisationSrv.getOrFail(organisation._id) must beSuccessfulTry(organisation)
        }
      }

      "create and get an organisation by its name" in db.transaction { implicit graph =>
        organisationSrv.create(Organisation(name = "orga2", "no description")) must beSuccessfulTry.which { organisation =>
          organisationSrv.getOrFail(organisation.name) must beSuccessfulTry(organisation)
        }
      }
    }
  }
}
