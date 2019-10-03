package org.thp.thehive.controllers.v0

import scala.util.Try

import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputCustomField
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv

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
    val customFieldSrv: CustomFieldSrv  = app.instanceOf[CustomFieldSrv]
    val db: Database                    = app.instanceOf[Database]

    s"[$name] custom field controller" should {
      "create a string custom field with options" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "name": "test",
                      "reference": "test",
                      "description": "test cf",
                      "type": "string",
                      "options": ["h", "m", "l"]

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

      "create a boolean custom field" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "name": "test bool",
                      "reference": "test-bool",
                      "description": "test cf bool",
                      "type": "boolean",
                      "mandatory": true

              }
            """.stripMargin))
        val result = customFieldCtr.create(request)

        status(result) shouldEqual 201

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual "test-bool"
        outputCustomField.name shouldEqual "test bool"
        outputCustomField.description shouldEqual "test cf bool"
        outputCustomField.`type` shouldEqual "boolean"
        outputCustomField.mandatory must beTrue
      }

      "create an integer custom field with options" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "name": "test int",
                      "reference": "test int",
                      "description": "test cf int",
                      "type": "integer",
                      "options": [1, 2, 3]

              }
            """.stripMargin))
        val result = customFieldCtr.create(request)

        status(result) shouldEqual 201

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual "test int"
        outputCustomField.name shouldEqual "test int"
        outputCustomField.description shouldEqual "test cf int"
        outputCustomField.`type` shouldEqual "integer"
        outputCustomField.options must containAllOf(Seq(JsNumber(1), JsNumber(2), JsNumber(3)))
        outputCustomField.mandatory must beFalse
      }

      "create a float custom field" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "name": "test float",
                      "reference": "test float",
                      "description": "test cf float",
                      "type": "float"

              }
            """.stripMargin))
        val result = customFieldCtr.create(request)

        status(result) shouldEqual 201

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual "test float"
        outputCustomField.name shouldEqual "test float"
        outputCustomField.description shouldEqual "test cf float"
        outputCustomField.`type` shouldEqual "float"
      }

      "create a date custom field with options and list all available" in {
        val request = FakeRequest("POST", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "name": "test date",
                      "reference": "test date",
                      "description": "test cf date",
                      "type": "date",
                      "mandatory": false
              }
            """.stripMargin))
        val result = customFieldCtr.create(request)

        status(result) shouldEqual 201

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual "test date"
        outputCustomField.name shouldEqual "test date"
        outputCustomField.description shouldEqual "test cf date"
        outputCustomField.`type` shouldEqual "date"
        outputCustomField.mandatory must beFalse

        val requestList = FakeRequest("GET", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
        val res = customFieldCtr.list(requestList)

        status(res) shouldEqual 200

        contentAsJson(res).as[List[OutputCustomField]] must not(beEmpty)
      }

      "remove a custom field" in {
        val l = db.roTransaction(graph => customFieldSrv.initSteps(graph).toList)

        l must not(beEmpty)

        val cf = l.head

        val request = FakeRequest("DELETE", s"/api/customField/${cf._id}")
          .withHeaders("user" -> "admin@thehive.local")
        val result = customFieldCtr.delete(cf._id)(request)

        status(result) shouldEqual 204

        val newList = db.roTransaction(graph => customFieldSrv.initSteps(graph).toList)

        newList.find(_._id == cf._id) must beNone
      }

      "update a string custom field" in {
        val l = db.roTransaction(graph => customFieldSrv.initSteps(graph).toList)

        l must not(beEmpty)

        val cf = l.find(_.`type` == CustomFieldString)

        cf must beSome

        val request = FakeRequest("PATCH", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "options": ["fear", "laughing"],
                      "description": "test cf updated",
                      "mandatory": true,
                      "reference": "las vegas"

              }
            """.stripMargin))
        val result = customFieldCtr.update(cf.get._id)(request)

        status(result) shouldEqual 200

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.reference shouldEqual cf.get.name
        outputCustomField.name shouldEqual cf.get.name
        outputCustomField.description shouldEqual "test cf updated"
        outputCustomField.`type` shouldEqual "string"
        outputCustomField.mandatory must beTrue
        outputCustomField.options must containAllOf(Seq(JsString("fear"), JsString("laughing")))
      }

      "update a date custom field" in {
        val l = db.roTransaction(graph => customFieldSrv.initSteps(graph).toList)

        l must not(beEmpty)

        val cf = l.find(_.`type` == CustomFieldDate)

        cf must beSome

        val request = FakeRequest("PATCH", s"/api/customField")
          .withHeaders("user" -> "admin@thehive.local")
          .withJsonBody(Json.parse("""
              {

                      "description": "test date cf updated"

              }
            """.stripMargin))
        val result = customFieldCtr.update(cf.get._id)(request)

        status(result) shouldEqual 200

        val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

        outputCustomField.description shouldEqual "test date cf updated"
        outputCustomField.`type` shouldEqual "date"
      }
    }
  }

}
