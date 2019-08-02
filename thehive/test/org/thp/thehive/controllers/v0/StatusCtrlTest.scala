package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.auth.{AuthCapability, AuthSrv, UserSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.scalligraph.{AppBuilder, ScalligraphApplicationLoader}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{Connector, LocalUserSrv}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AbstractController
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.Try

class StatusCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer
  val authSrv: AuthSrv           = mock[AuthSrv]
  authSrv.capabilities returns Set(AuthCapability.changePassword)
  authSrv.name returns "authSrvName"

  val fakeCortexConnector = new Connector {
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

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .multiBindInstance[Connector](fakeCortexConnector)
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
      .bindInstance[AuthSrv](authSrv)

    specs(dbProvider.name, app)
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

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
