package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.traversal.TraversalOps
import org.thp.thehive.connector.cortex.TestAppBuilder
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class JobCtrlTest extends PlaySpecification with TestAppBuilder with TraversalOps {

  "job controller" should {
    "get a job" in testApp { app =>
      import app._
      import app.cortexModule.jobCtrl
      import app.thehiveModule._

      val observable = database.roTransaction { implicit graph =>
        observableSrv.startTraversal.has(_.message, "Some weird domain").getOrFail("Observable").get
      }

      val requestSearch = FakeRequest("POST", s"/api/connector/cortex/job/_search?range=0-200&sort=-startDate")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse(s"""
              {
                 "query":{
                    "_and":[
                       {
                          "_parent":{
                             "_type":"case_artifact",
                             "_query":{
                                "_id":"${observable._id}"
                             }
                          }
                       }
                    ]
                 }
              }
            """.stripMargin))
      val resultSearch = jobCtrl.search(requestSearch)
      status(resultSearch) shouldEqual 200
    }

    "get stats for a job" in testApp { app =>
      import app.cortexModule.jobCtrl

      val request = FakeRequest("POST", s"/api/connector/cortex/job/_stats")
        .withHeaders("user" -> "certuser@thehive.local")
        .withJsonBody(Json.parse(s"""
                                   {
                                     "query": {
                                       "_and": [{
                                         "_in": {
                                           "_field": "status",
                                           "_values": ["Waiting", "InProgress"]
                                         }
                                       }, {
                                         "analyzerId": "anaTest1"
                                       }]
                                     },
                                     "stats": [{
                                       "_agg": "field",
                                       "_field": "status",
                                       "_select": [{ "_agg": "count"}]
                                     }, {
                                       "_agg": "count"
                                     }]
                                   }
            """.stripMargin))
      val result = jobCtrl.stats(request)

      status(result) shouldEqual 200
    }
  }
}
