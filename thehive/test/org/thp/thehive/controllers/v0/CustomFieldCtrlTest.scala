package org.thp.thehive.controllers.v0

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputCustomField
import org.thp.thehive.models._
import org.thp.thehive.services.CustomFieldSrv
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.test.{FakeRequest, PlaySpecification}

class CustomFieldCtrlTest extends PlaySpecification with TestAppBuilder {
  "custom field controller" should {
    "create a string custom field with options" in testApp { app =>
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
      val result = app[CustomFieldCtrl].create(request)

      status(result) shouldEqual 201

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual "test"
      outputCustomField.name shouldEqual "test"
      outputCustomField.description shouldEqual "test cf"
      outputCustomField.`type` shouldEqual "string"
      outputCustomField.options must containAllOf(Seq(JsString("h"), JsString("m"), JsString("l")))
      outputCustomField.mandatory shouldEqual false
    }

    "create a boolean custom field" in testApp { app =>
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
      val result = app[CustomFieldCtrl].create(request)

      status(result) shouldEqual 201

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual "test-bool"
      outputCustomField.name shouldEqual "test bool"
      outputCustomField.description shouldEqual "test cf bool"
      outputCustomField.`type` shouldEqual "boolean"
      outputCustomField.mandatory must beTrue
    }

    "create an integer custom field with options" in testApp { app =>
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
      val result = app[CustomFieldCtrl].create(request)

      status(result) shouldEqual 201

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual "test int"
      outputCustomField.name shouldEqual "test int"
      outputCustomField.description shouldEqual "test cf int"
      outputCustomField.`type` shouldEqual "integer"
      outputCustomField.options must containAllOf(Seq(JsNumber(1), JsNumber(2), JsNumber(3)))
      outputCustomField.mandatory must beFalse
    }

    "create a float custom field" in testApp { app =>
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
      val result = app[CustomFieldCtrl].create(request)

      status(result) shouldEqual 201

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual "test float"
      outputCustomField.name shouldEqual "test float"
      outputCustomField.description shouldEqual "test cf float"
      outputCustomField.`type` shouldEqual "float"
    }

    "create a date custom field with options and list all available" in testApp { app =>
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
      val result = app[CustomFieldCtrl].create(request)

      status(result) shouldEqual 201

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual "test date"
      outputCustomField.name shouldEqual "test date"
      outputCustomField.description shouldEqual "test cf date"
      outputCustomField.`type` shouldEqual "date"
      outputCustomField.mandatory must beFalse

      val requestList = FakeRequest("GET", s"/api/customField")
        .withHeaders("user" -> "admin@thehive.local")
      val res = app[CustomFieldCtrl].list(requestList)

      status(res) shouldEqual 200

      contentAsJson(res).as[List[OutputCustomField]] must not(beEmpty)
    }

    "remove a custom field" in testApp { app =>
      val l = app[Database].roTransaction(graph => app[CustomFieldSrv].startTraversal(graph).toSeq)

      l must not(beEmpty)

      val cf = l.head

      val request = FakeRequest("DELETE", s"/api/customField/${cf._id}")
        .withHeaders("user" -> "admin@thehive.local")
      val result = app[CustomFieldCtrl].delete(cf._id.toString)(request)

      status(result) shouldEqual 204

      val newList = app[Database].roTransaction(graph => app[CustomFieldSrv].startTraversal(graph).toSeq)

      newList.find(_._id == cf._id) must beNone
    }

    "update a string custom field" in testApp { app =>
      val l = app[Database].roTransaction(graph => app[CustomFieldSrv].startTraversal(graph).toSeq)

      l must not(beEmpty)

      val cf = l.find(_.`type` == CustomFieldType.string)

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
      val result = app[CustomFieldCtrl].update(cf.get._id.toString)(request)

      status(result) shouldEqual 200

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.reference shouldEqual cf.get.name
      outputCustomField.name shouldEqual cf.get.name
      outputCustomField.description shouldEqual "test cf updated"
      outputCustomField.`type` shouldEqual "string"
      outputCustomField.mandatory must beTrue
      outputCustomField.options must containAllOf(Seq(JsString("fear"), JsString("laughing")))
    }

    "update a date custom field" in testApp { app =>
      val l = app[Database].roTransaction(graph => app[CustomFieldSrv].startTraversal(graph).toSeq)

      l must not(beEmpty)

      val cf = l.find(_.`type` == CustomFieldType.date)

      cf must beSome

      val request = FakeRequest("PATCH", s"/api/customField")
        .withHeaders("user" -> "admin@thehive.local")
        .withJsonBody(Json.parse("""
              {

                      "description": "test date cf updated"

              }
            """.stripMargin))
      val result = app[CustomFieldCtrl].update(cf.get._id.toString)(request)

      status(result) shouldEqual 200

      val outputCustomField: OutputCustomField = contentAsJson(result).as[OutputCustomField]

      outputCustomField.description shouldEqual "test date cf updated"
      outputCustomField.`type` shouldEqual "date"
    }
  }
}
