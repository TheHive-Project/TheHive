package org.thp.thehive.controllers.v0

import org.thp.scalligraph.{ScalligraphApplication, ScalligraphApplicationLoader}
import org.thp.thehive.TheHiveModule
import org.thp.thehive.models.{HealthStatus, User}
import org.thp.thehive.services.Connector
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.mvc.AbstractController
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class StatusCtrlTest extends PlaySpecification with TestAppBuilder {
  val config: Configuration = Configuration.load(Environment.simple())

  override def buildTestModule(app: ScalligraphApplication): TheHiveModule = {
    val thehiveModule = super.buildTestModule(app)
    thehiveModule.connectors += fakeCortexConnector
    thehiveModule
  }

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

    override val health: HealthStatus.Value = HealthStatus.Warning
  }

  "status controller" should {

    "return proper status" in testApp { app =>
      import app.thehiveModuleV0._
      app.schemas.foreach(_.update(app.database)) // need to get schema status

      val request = FakeRequest("GET", s"/api/v0/status")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = statusCtrl.get()(request)

      status(result) shouldEqual 200

      val resultJson = contentAsJson(result)
      val expectedJson = Json.obj(
        "versions" -> Json.obj(
          "Scalligraph" -> getVersion(classOf[ScalligraphApplicationLoader]),
          "TheHive"     -> getVersion(classOf[User]),
          "Play"        -> getVersion(classOf[AbstractController])
        ),
        "connectors" -> Json.obj(
          "cortex" -> Json.obj(
            "enabled" -> true,
            "status"  -> "Ok",
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
        "config" -> Json.obj(
          "protectDownloadsWith" -> config.get[String]("datastore.attachment.password"),
          "authType"             -> Seq("header", "local", "key"),
          "capabilities"         -> Seq("changePassword", "setPassword", "authByKey", "mfa"),
          "ssoAutoLogin"         -> config.get[Boolean]("user.autoCreateOnSso"),
          "pollingDuration"      -> 1000,
          "freeTagDefaultColour" -> "#000000"
        ),
        "schemaStatus" -> Json.arr(
          Json.obj("name" -> "thehive", "currentVersion" -> 87, "expectedVersion" -> 87, "error" -> JsNull)
        )
      )

      resultJson shouldEqual expectedJson
    }

    "be healthy" in testApp { app =>
      import app.thehiveModuleV0._

      val request = FakeRequest("GET", s"/api/v0/health")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = statusCtrl.health(request)

      status(result) shouldEqual 200
      contentAsString(result) shouldEqual "Warning"
    }
  }

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")
}
