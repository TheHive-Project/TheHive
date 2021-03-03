package org.thp.thehive.controllers.v0

import akka.stream.Materializer
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.dto.v0.OutputTag
import org.thp.thehive.services.TagSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class TagCtrlTest extends PlaySpecification with TestAppBuilder {
  "tag controller" should {
    "get a tag" in testApp { app =>
      // Get a tag id first
      val tags = app[Database].roTransaction(implicit graph => app[TagSrv].startTraversal.toSeq)
      val tag  = tags.head

      val request = FakeRequest("GET", s"/api/tag/${tag._id}")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = app[TagCtrl].get(tag._id.toString)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "search a tag" in testApp { app =>
      val json = Json.parse("""{
         "range":"all",
         "sort":[
            "-updatedAt",
            "-createdAt"
         ],
         "query":{
            "_and":[
               {
                  "_or":[
                     {
                        "predicate":"testDomain"
                     },
                     {
                        "predicate":"hello"
                     }
                  ]
               }
            ]
         }
          }""".stripMargin)

      val request = FakeRequest("POST", s"/api/tag/_search")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(json)
      val result = app[TagCtrl].search(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

      val l = contentAsJson(result)(defaultAwaitTimeout, app[Materializer]).as[List[OutputTag]]

      l.length shouldEqual 2
      l.find(_.predicate == "testDomain") must beSome
    }
  }
}
