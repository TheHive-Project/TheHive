package org.thp.thehive.connector.cortex.services

import scala.util.Try

import play.api.test.{NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.client._
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services._

class ResponderSrvTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "user1", organisation = "cert", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")
      .bindToProvider[CortexConfig, TestCortexConfigProvider]

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment =
    s"[$name] responder service" should {
      val responderSrv                      = app.instanceOf[ResponderSrv]
      val taskSrv: TaskSrv                  = app.instanceOf[TaskSrv]
      implicit val authContext: AuthContext = dummyUserSrv.authContext
      val db                                = app.instanceOf[Database]

      "fetch responders by type" in {
        val t = db.roTransaction { graph =>
          taskSrv.initSteps(graph).toList.headOption
        }

        t must beSome.which { task =>
          val r = await(responderSrv.getRespondersByType("case_task", task._id))

          r.find(_._1.name == "respTest1") must beSome
        }
      }
    }
}
