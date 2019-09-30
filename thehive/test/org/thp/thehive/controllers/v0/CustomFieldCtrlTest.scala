package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputCustomField
import org.thp.thehive.models._
import play.api.libs.json.{JsString, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class CustomFieldCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(userId = "admin@thehive.local", permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val customFieldCtr: CustomFieldCtrl = app.instanceOf[CustomFieldCtrl]

    s"[$name] custom field controller" should {
      "create a string custom field with options" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "user1@thehive.local")
          .withJsonBody(Json.parse(
            """
              {
                  "value": {
                      "name": "test",
                      "reference": "test",
                      "description": "test cf",
                      "type": "string",
                      "options": ["h", "m", "l"]
                  }
              }  
            """.stripMargin))
        val result = customFieldCtr.create(request)

        status(result) shouldEqual 201

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual "test"
        outputCustomField.name shouldEqual "test"
        outputCustomField.description shouldEqual "test cf"
        outputCustomField.`type` shouldEqual "string"
        outputCustomField.options must containAllOf(Seq(JsString("h"), JsString("m"), JsString("l")))
        outputCustomField.mandatory shouldEqual false
      }
    }
  }

}
