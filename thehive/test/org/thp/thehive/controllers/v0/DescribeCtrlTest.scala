package org.thp.thehive.controllers.v0

import org.specs2.matcher.MatchResult
import org.thp.thehive.TestAppBuilder
import play.api.libs.json.{JsObject, Reads}
import play.api.test.{FakeRequest, PlaySpecification}

class DescribeCtrlTest extends PlaySpecification with TestAppBuilder {

  def checkValues[T](l: List[JsObject], attribute: String, values: List[T])(implicit r: Reads[List[T]]): MatchResult[Option[JsObject]] =
    l.find(o => (o \ "name").as[String] == attribute) must beSome.which(
      is => (is \ "values").as[List[T]] must containAllOf(values)
    )

  "describe controller" should {

    "describe a model by its name if existing" in testApp { app =>
      val request = FakeRequest("GET", s"/api/dashboard/case")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[DescribeCtrl].describe("case")(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val jsonResult = contentAsJson(result).as[JsObject]
      val attributes = (jsonResult \ "attributes").as[List[JsObject]]

      attributes must not(beEmpty)
      (jsonResult \ "label").as[String] shouldEqual "case"
      (jsonResult \ "path").as[String] shouldEqual "/case"
      (jsonResult \ "attributes" \\ "name").map(_.as[String]).toList must contain("customFields.string1")
      checkValues[String](attributes, "impactStatus", List("NoImpact", "WithImpact", "NotApplicable"))
      checkValues[String](attributes, "status", List("Open", "Resolved", "Deleted", "Duplicated"))
      checkValues[String](attributes, "resolutionStatus", List("FalsePositive", "Duplicated", "Indeterminate", "TruePositive", "Other"))
      checkValues[Int](attributes, "pap", List(0, 1, 2, 3))
      checkValues[Int](attributes, "tlp", List(0, 1, 2, 3))

      val requestGet = FakeRequest("GET", s"/api/describe/yolo")
        .withHeaders("user" -> "certuser@thehive.local")
      val resultGet = app[DescribeCtrl].describe("yolo")(requestGet)

      status(resultGet) must equalTo(404).updateMessage(s => s"$s\n${contentAsString(resultGet)}")
    }

    "describe all available models" in testApp { app =>
      val request = FakeRequest("GET", s"/api/describe/_all")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[DescribeCtrl].describeAll(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
      contentAsJson(result).as[JsObject].keys must not(beEmpty)
    }
  }
}
