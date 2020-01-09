package org.thp.thehive.controllers.v0

import org.thp.scalligraph.{AppBuilder, ScalligraphApplicationLoader}
import org.thp.thehive.models.HealthStatus
import org.thp.thehive.services.Connector
import org.thp.thehive.{TestAppBuilder, TheHiveModule}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AbstractController
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.{Configuration, Environment}

class StatusCtrlTest extends PlaySpecification with TestAppBuilder {
  val config: Configuration = Configuration.load(Environment.simple())

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

    override def health: HealthStatus.Value = HealthStatus.Warning
  }

  override def appConfigure: AppBuilder = super.appConfigure.multiBindInstance[Connector](fakeCortexConnector)

  "status controller" should {

    "return proper status" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/status")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[StatusCtrl].get()(request)

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
        "health" -> Json.obj("elasticsearch" -> "UNKNOWN"),
        "config" -> Json.obj(
          "protectDownloadsWith" -> config.get[String]("datastore.attachment.password"),
          "authType"             -> Seq("local", "key", "header"),
          "capabilities"         -> Seq("changePassword", "setPassword", "authByKey"),
          "ssoAutoLogin"         -> config.get[Boolean]("user.autoCreateOnSso")
        )
      )

      resultJson shouldEqual expectedJson
    }

    "be healthy" in testApp { app =>
      val request = FakeRequest("GET", s"/api/v0/health")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[StatusCtrl].health(request)

      status(result) shouldEqual 200
      contentAsString(result) shouldEqual "Warning"
    }
  }

  private def getVersion(c: Class[_]) = Option(c.getPackage.getImplementationVersion).getOrElse("SNAPSHOT")
}
