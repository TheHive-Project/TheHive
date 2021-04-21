package org.thp.thehive.controllers.v0

import org.thp.scalligraph.traversal.TraversalOps._
import play.api.test.{FakeRequest, PlaySpecification}

class TagCtrlTest extends PlaySpecification with TestAppBuilder {
  "tag controller" should {
    "get a tag" in testApp { app =>
      import app._
      import app.thehiveModule._
      import app.thehiveModuleV0._

      // Get a tag id first
      val tags = database.roTransaction(implicit graph => tagSrv.startTraversal.toSeq)
      val tag  = tags.head

      val request = FakeRequest("GET", s"/api/tag/${tag._id}")
        .withHeaders("user" -> "certuser@thehive.local")
      val result = tagCtrl.get(tag._id.toString)(request)

      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
    }

    "search a tag" in testApp { app =>
//      val json = Json.parse("""{
//         "range":"all",
//         "sort":[
//            "-updatedAt",
//            "-createdAt"
//         ],
//         "query":{
//            "_and":[
//               {
//                  "_or":[
//                     {
//                        "predicate":"testDomain"
//                     },
//                     {
//                        "predicate":"t2"
//                     }
//                  ]
//               }
//            ]
//         }
//          }""".stripMargin)
//
//      val request = FakeRequest("POST", s"/api/tag/_search")
//        .withHeaders("user" -> "certuser@thehive.local")
//        .withJsonBody(json)
//      val result = tagCtrl.search(request)
//
//      status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")
//
//      val l = contentAsJson(result)(defaultAwaitTimeout, materializer).as[List[OutputTag]]
//
//      l.length shouldEqual 2
//      l.find(_.predicate == "testDomain") must beSome
      pending("freetags created in test database are owned by organisation admin")
    }
  }
}
