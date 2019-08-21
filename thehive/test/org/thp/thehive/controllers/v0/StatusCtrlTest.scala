package org.thp.thehive.controllers.v0

import scala.util.Try

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AbstractController
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.scalligraph.{AppBuilder, ScalligraphApplicationLoader}
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services.Connector
import org.thp.thehive.{TestAppBuilder, TheHiveModule}

class StatusCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer
  val authSrv: AuthSrv           = mock[AuthSrv]
  authSrv.capabilities returns Set(AuthCapability.changePassword)
  authSrv.name returns "authSrvName"

  val fakeCortexConnector: Connector = new Connector {
    override val name: String = "cortex"
    override def status: JsObject =
      Json.obj(
        "enabled" -> true,
        "status"  -> "OK",
        "servers" -> Json.arr(
          Json.obj(
            "name"    -> "interne",
            "version" -> "2.x.x",
            "status"  -> "OK"
          )
        )
      )
  }

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .`override`(
        _.multiBindInstance[Connector](fakeCortexConnector)
          .bindInstance[AuthSrv](authSrv)
      )

    specs(dbProvider.name, app)
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")

  def specs(name: String, app: AppBuilder): Fragment = {
    val statusCtrl = app.instanceOf[StatusCtrl]

    s"status controller" should {

      "return proper status" in {
        val request = FakeRequest("GET", s"/api/v0/status")
          .withHeaders("user" -> "user1")
        val result = statusCtrl.get()(request)

        status(result) shouldEqual 200

        val resultJson = contentAsJson(result)
        val expectedJson = Json.obj(
          "versions" -> Json.obj(
            "Scalligraph" -> getVersion(classOf[ScalligraphApplicationLoader]),
            "TheHive"     -> getVersion(classOf[TheHiveModule]),
            "Play"        -> getVersion(classOf[AbstractController])
          ),
          "connectors" -> Json.obj(
            "cortex" -> Json.obj(
              "enabled" -> true,
              "servers" -> Json.arr(
                Json.obj(
                  "name"    -> "interne",
                  "version" -> "2.x.x",
                  "status"  -> "OK"
                )
              ),
              "status" -> "OK"
            )
          ),
          "health" -> Json.obj("elasticsearch" -> "UNKNOWN"),
          "config" -> Json.obj(
            "protectDownloadsWith" -> config.get[String]("datastore.attachment.password"),
            "authType"             -> "authSrvName",
            "capabilities"         -> Seq("changePassword"),
            "ssoAutoLogin"         -> config.get[Boolean]("auth.sso.autologin")
          )
        )

        resultJson shouldEqual expectedJson
      }

      "be healthy" in {
        val request = FakeRequest("GET", s"/api/v0/health")
          .withHeaders("user" -> "user1")
        val result = statusCtrl.health(request)

        status(result) shouldEqual 200
        contentAsString(result) shouldEqual "Warning"
      }
    }
  }
}
